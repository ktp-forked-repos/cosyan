package com.cosyan.db.lang.sql;

import org.junit.Ignore;
import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.QueryResult;

public class InsertIntoLargeTest extends UnitTestBase {

  @Test
  @Ignore
  public void testInsertIntoTable() throws Exception {
    execute("create table t1 (a varchar, b integer, c float);");
    long t = System.currentTimeMillis();
    for (int i = 0; i < 5000; i++) {
      StringBuilder sb = new StringBuilder();
      sb.append("insert into t1 values ('abcdefghijkl', 123456789, 2.0)");
      for (int j = 0; j < 999; j++) {
        sb.append(",('abcdefghijkl', 123456789, 2.0)");
      }
      sb.append(";");
      execute(sb.toString());
    }
    System.out.println(System.currentTimeMillis() - t);
   // ExposedTableReader reader = compiler.query(parser.parse("select count(1) as c from t1;")).reader();
   // assertEquals(ImmutableMap.of("c", 100000L), reader.readColumns());
  }

  @Test
  @Ignore
  public void testInsertIntoTableWithIndex() throws Exception {
    execute("create table t2 (a varchar, b integer unique not null, c float);");
    long t = System.currentTimeMillis();
    for (int i = 0; i < 100000; i++) {
      execute("insert into t2 values ('x', " + i + ", 2.0);");
    }
    System.out.println(System.currentTimeMillis() - t);
    QueryResult result = query ("select count(1) as c from t2;");
    assertValues(new Object[][] { { 100000L } }, result);
  }
}
