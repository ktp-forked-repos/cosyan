package com.cosyan.db.sql;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.text.ParseException;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

import com.cosyan.db.conf.Config;
import com.cosyan.db.io.IOTestUtil.DummyMaterializedTableMeta;
import com.cosyan.db.io.TableReader.ExposedTableReader;
import com.cosyan.db.lock.LockManager;
import com.cosyan.db.model.ColumnMeta.BasicColumn;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.MetaRepo;
import com.cosyan.db.model.MetaRepo.ModelException;
import com.cosyan.db.model.TableMeta.ExposedTableMeta;
import com.cosyan.db.sql.SelectStatement.Select;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class TableReaderTest {

  private static MetaRepo metaRepo;
  private static Parser parser;

  @BeforeClass
  public static void setUp() throws IOException, ModelException, ParseException {
    Properties props = new Properties();
    props.setProperty(Config.DATA_DIR, "/tmp/data");
    metaRepo = new MetaRepo(new Config(props), new LockManager());
    parser = new Parser();
    metaRepo.registerTable("table", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "a", new BasicColumn(0, "a", DataTypes.StringType),
            "b", new BasicColumn(1, "b", DataTypes.LongType),
            "c", new BasicColumn(2, "c", DataTypes.DoubleType)),
        new Object[][] {
            new Object[] { "abc", 1L, 1.0 },
            new Object[] { "xyz", 5L, 6.7 } }));

    metaRepo.registerTable("large", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "a", new BasicColumn(0, "a", DataTypes.StringType),
            "b", new BasicColumn(1, "b", DataTypes.LongType),
            "c", new BasicColumn(2, "c", DataTypes.DoubleType)),
        new Object[][] {
            new Object[] { "a", 1L, 2.0 },
            new Object[] { "a", 3L, 4.0 },
            new Object[] { "b", 5L, 6.0 },
            new Object[] { "b", 7L, 8.0 } }));

    metaRepo.registerTable("left", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "a", new BasicColumn(0, "a", DataTypes.StringType),
            "b", new BasicColumn(1, "b", DataTypes.LongType)),
        new Object[][] {
            new Object[] { "a", 1L },
            new Object[] { "b", 1L },
            new Object[] { "c", 5L } }));

    metaRepo.registerTable("right", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "x", new BasicColumn(0, "x", DataTypes.StringType),
            "y", new BasicColumn(1, "y", DataTypes.LongType)),
        new Object[][] {
            new Object[] { "a", 2L },
            new Object[] { "c", 6L } }));

    metaRepo.registerTable("dupl", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "x", new BasicColumn(0, "x", DataTypes.StringType),
            "y", new BasicColumn(1, "y", DataTypes.LongType)),
        new Object[][] {
            new Object[] { "a", 1L },
            new Object[] { "a", 5L } }));

    metaRepo.registerTable("null", new DummyMaterializedTableMeta(
        ImmutableMap.of(
            "a", new BasicColumn(0, "a", DataTypes.StringType, true, false),
            "b", new BasicColumn(1, "b", DataTypes.LongType, true, false),
            "c", new BasicColumn(2, "c", DataTypes.DoubleType, true, false)),
        new Object[][] {
            new Object[] { DataTypes.NULL, 1L, 2.0 },
            new Object[] { "b", DataTypes.NULL, 4.0 },
            new Object[] { "c", 5L, DataTypes.NULL } }));

  }

  private ExposedTableMeta query(SyntaxTree tree) throws ModelException {
    return ((Select) Iterables.getOnlyElement(tree.getRoots())).compile(metaRepo);
  }

  @Test
  public void testReadFirstLine() throws Exception {
    SyntaxTree tree = parser.parse("select * from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "abc", "b", 1L, "c", 1.0), reader.readColumns());
  }

  @Test
  public void testTableAlias() throws Exception {
    SyntaxTree tree = parser.parse("select t.a from table as t;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "abc"), reader.readColumns());
  }

  @Test
  public void testReadArithmeticExpressions1() throws Exception {
    SyntaxTree tree = parser.parse("select b + 2, c * 3.0, c / b, c - 1, 3 % 2 from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("_c0", 3L, "_c1", 3.0, "_c2", 1.0, "_c3", 0.0, "_c4", 1L), reader.readColumns());
  }

  @Test
  public void testReadArithmeticExpressions2() throws Exception {
    SyntaxTree tree = parser.parse("select a + 'xyz' from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("_c0", "abcxyz"), reader.readColumns());
  }

  @Test
  public void testReadLogicExpressions1() throws Exception {
    SyntaxTree tree = parser.parse("select b = 1, b < 0.0, c > 0, c <= 1, c >= 2.0 from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("_c0", true, "_c1", false, "_c2", true, "_c3", true, "_c4", false),
        reader.readColumns());
  }

  @Test
  public void testReadLogicExpressions2() throws Exception {
    SyntaxTree tree = parser.parse("select a = 'abc', a > 'b', a < 'x', a >= 'ab', a <= 'x' from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("_c0", true, "_c1", false, "_c2", true, "_c3", true, "_c4", true),
        reader.readColumns());
  }

  @Test
  public void testReadStringFunction() throws Exception {
    SyntaxTree tree = parser.parse("select length(a), upper(a), substr(a, 1, 1) from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("_c0", 3L, "_c1", "ABC", "_c2", "b"), reader.readColumns());
  }

  @Test
  public void testWhere() throws Exception {
    SyntaxTree tree = parser.parse("select * from table where b > 1;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "xyz", "b", 5L, "c", 6.7), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerSelect() throws Exception {
    SyntaxTree tree = parser.parse("select * from (select * from table where b > 1);");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "xyz", "b", 5L, "c", 6.7), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testAliasing() throws Exception {
    SyntaxTree tree = parser.parse("select b + 2 as x, c * 3.0 as y from table;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("x", 3L, "y", 3.0), reader.readColumns());
  }

  @Test
  public void testGlobalAggregate() throws Exception {
    SyntaxTree tree = parser.parse("select sum(b) as b, sum(c) as c from large;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("b", 16L, "c", 20.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testAggregatorsSum() throws Exception {
    SyntaxTree tree = parser.parse("select sum(1) as s1, sum(2.0) as s2, sum(b) as sb, sum(c) as sc from large;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("s1", 4L, "s2", 8.0, "sb", 16L, "sc", 20.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testAggregatorsCount() throws Exception {
    SyntaxTree tree = parser.parse("select count(1) as c1, count(a) as ca, count(b) as cb, count(c) as cc from large;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("c1", 4L, "ca", 4L, "cb", 4L, "cc", 4L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testAggregatorsMax() throws Exception {
    SyntaxTree tree = parser.parse("select max(a) as a, max(b) as b, max(c) as c from large;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b", "b", 7L, "c", 8.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testAggregatorsMin() throws Exception {
    SyntaxTree tree = parser.parse("select min(a) as a, min(b) as b, min(c) as c from large;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "c", 2.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testGroupBy() throws Exception {
    SyntaxTree tree = parser.parse("select a, sum(b) as b, sum(c) as c from large group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 4L, "c", 6.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 12L, "c", 14.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testExpressionInGroupBy() throws Exception {
    SyntaxTree tree = parser.parse("select a, sum(b + 1) as b, sum(c * 2.0) as c from large group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 6L, "c", 12.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 14L, "c", 28.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testExpressionFromGroupBy() throws Exception {
    SyntaxTree tree = parser.parse("select a, sum(b) + sum(c) as b from large group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 10.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 26.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testGroupByMultipleKey() throws Exception {
    SyntaxTree tree = parser.parse("select a, b, sum(c) as c from large group by a, b;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "c", 2.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "a", "b", 3L, "c", 4.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 5L, "c", 6.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 7L, "c", 8.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testGroupByAttrOrder() throws Exception {
    SyntaxTree tree = parser.parse("select sum(c) as c, a, a as d, sum(b) as b from large group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("c", 6.0, "a", "a", "d", "a", "b", 4L), reader.readColumns());
    assertEquals(ImmutableMap.of("c", 14.0, "a", "b", "d", "b", "b", 12L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testGroupByAndWhere() throws Exception {
    SyntaxTree tree = parser.parse("select a, sum(b) as b from large where c % 4 = 0 group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 3L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 7L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testHaving() throws Exception {
    SyntaxTree tree = parser.parse("select a from large group by a having sum(b) > 10;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b"), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testOrderBy() throws Exception {
    SyntaxTree tree = parser.parse("select b from large order by b desc;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("b", 7L), reader.readColumns());
    assertEquals(ImmutableMap.of("b", 5L), reader.readColumns());
    assertEquals(ImmutableMap.of("b", 3L), reader.readColumns());
    assertEquals(ImmutableMap.of("b", 1L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testOrderByMultipleKeys() throws Exception {
    SyntaxTree tree = parser.parse("select a, b from large order by a desc, b;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b", "b", 5L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", 7L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "a", "b", 1L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "a", "b", 3L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoin1() throws Exception {
    SyntaxTree tree = parser.parse("select * from left inner join right on a = x;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "x", "a", "y", 2L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L, "x", "c", "y", 6L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoin2() throws Exception {
    SyntaxTree tree = parser.parse("select * from right inner join left on x = a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("x", "a", "y", 2L, "a", "a", "b", 1L), reader.readColumns());
    assertEquals(ImmutableMap.of("x", "c", "y", 6L, "a", "c", "b", 5L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoinDuplication1() throws Exception {
    SyntaxTree tree = parser.parse("select * from left inner join dupl on a = x;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "x", "a", "y", 1L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "x", "a", "y", 5L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoinDuplication2() throws Exception {
    SyntaxTree tree = parser.parse("select * from dupl inner join left on x = a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("x", "a", "y", 1L, "a", "a", "b", 1L), reader.readColumns());
    assertEquals(ImmutableMap.of("x", "a", "y", 5L, "a", "a", "b", 1L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoinTableAlias() throws Exception {
    SyntaxTree tree = parser.parse("select l.a, l.b, r.x, r.y from left as l inner join right as r on l.a = r.x;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "b", 1L, "x", "a", "y", 2L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L, "x", "c", "y", 6L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoinSubSelectAlias() throws Exception {
    SyntaxTree tree = parser.parse("select l.a, r.x from left as l inner join "
        + "(select x from right) as r on l.a = r.x;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "a", "x", "a"), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "x", "c"), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testInnerJoinAliasSolvesNameCollision() throws Exception {
    SyntaxTree tree = parser.parse("select l.a as l, r.a as r from left as l inner join "
        + "(select x as a from right) as r on l.a = r.a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("l", "a", "r", "a"), reader.readColumns());
    assertEquals(ImmutableMap.of("l", "c", "r", "c"), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testReadLinesWithNull() throws Exception {
    SyntaxTree tree = parser.parse("select * from null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", DataTypes.NULL, "b", 1L, "c", 2.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "b", "b", DataTypes.NULL, "c", 4.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L, "c", DataTypes.NULL), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testNullInBinaryExpression() throws Exception {
    SyntaxTree tree = parser.parse("select a + 'x' as a, b * 2 as b, c - 1 as c from null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", DataTypes.NULL, "b", 2L, "c", 1.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "bx", "b", DataTypes.NULL, "c", 3.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "cx", "b", 10L, "c", DataTypes.NULL), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testNullInFuncCall() throws Exception {
    SyntaxTree tree = parser.parse("select length(a) as a from null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", DataTypes.NULL), reader.readColumns());
    assertEquals(ImmutableMap.of("a", 1L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", 1L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testNullInAggregation() throws Exception {
    SyntaxTree tree = parser.parse("select sum(b) as b, count(c) as c from null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("b", 6L, "c", 2L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testNullAsAggregationKey() throws Exception {
    SyntaxTree tree = parser.parse("select a, sum(b) as b from null group by a;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b", "b", 0L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L), reader.readColumns());
    assertEquals(ImmutableMap.of("a", DataTypes.NULL, "b", 1L), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testOrderByNull() throws Exception {
    SyntaxTree tree = parser.parse("select * from null order by b;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b", "b", DataTypes.NULL, "c", 4.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", DataTypes.NULL, "b", 1L, "c", 2.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L, "c", DataTypes.NULL), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test(expected = ModelException.class)
  public void testNullEquals() throws Exception {
    SyntaxTree tree = parser.parse("select * from null where b = null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testWhereIsNull() throws Exception {
    SyntaxTree tree = parser.parse("select * from null where b is null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", "b", "b", DataTypes.NULL, "c", 4.0), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test
  public void testWhereIsNotNull() throws Exception {
    SyntaxTree tree = parser.parse("select * from null where b is not null;");
    ExposedTableMeta ExposedTableMeta = query(tree);
    ExposedTableReader reader = ExposedTableMeta.reader();
    assertEquals(ImmutableMap.of("a", DataTypes.NULL, "b", 1L, "c", 2.0), reader.readColumns());
    assertEquals(ImmutableMap.of("a", "c", "b", 5L, "c", DataTypes.NULL), reader.readColumns());
    assertEquals(null, reader.readColumns());
  }

  @Test(expected = ModelException.class)
  public void testAggrInAggr() throws Exception {
    SyntaxTree tree = parser.parse("select sum(sum(b)) from large;");
    query(tree);
  }

  @Test(expected = ModelException.class)
  public void testNonKeyOutsideOfAggr() throws Exception {
    SyntaxTree tree = parser.parse("select b, sum(c) from large group by a;");
    query(tree);
  }
}