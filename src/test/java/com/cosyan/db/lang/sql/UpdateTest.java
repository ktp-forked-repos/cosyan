/*
 * Copyright 2018 Gergely Svigruha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cosyan.db.lang.sql;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.ErrorResult;
import com.cosyan.db.lang.transaction.Result.QueryResult;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.meta.MetaRepo.RuleException;
import com.cosyan.db.model.TableMultiIndex;
import com.cosyan.db.model.TableUniqueIndex;

public class UpdateTest extends UnitTestBase {

  @Test
  public void testUpdateAllRecords() throws Exception {
    execute("create table t1 (a varchar, b integer, c float);");
    execute("insert into t1 values ('x', 1, 2.0);");
    execute("insert into t1 values ('y', 3, 4.0);");
    QueryResult r1 = query("select * from t1;");
    assertHeader(new String[] { "a", "b", "c" }, r1);
    assertValues(new Object[][] {
        { "x", 1L, 2.0 },
        { "y", 3L, 4.0 } }, r1);

    execute("update t1 set b = b + 10, c = c * 2;");
    QueryResult r2 = query("select * from t1;");
    assertValues(new Object[][] {
        { "x", 11L, 4.0 },
        { "y", 13L, 8.0 } }, r2);
  }

  @Test
  public void testUpdateWithWhere() throws Exception {
    execute("create table t2 (a varchar, b integer, c float);");
    execute("insert into t2 values ('x', 1, 2.0);");
    execute("insert into t2 values ('y', 3, 4.0);");
    QueryResult r1 = query("select * from t2;");
    assertHeader(new String[] { "a", "b", "c" }, r1);
    assertValues(new Object[][] {
        { "x", 1L, 2.0 },
        { "y", 3L, 4.0 } }, r1);

    execute("update t2 set a = 'z' where a = 'x';");
    QueryResult r2 = query("select * from t2;");
    assertValues(new Object[][] {
        { "y", 3L, 4.0 },
        { "z", 1L, 2.0 } }, r2);
  }

  @Test
  public void testUpdateWithIndex() throws Exception {
    execute("create table t3 (a varchar unique not null, b integer);");
    execute("insert into t3 values ('x', 1);");
    execute("insert into t3 values ('y', 2);");

    ErrorResult r1 = error("update t3 set a = 'y' where a = 'x';");
    assertError(RuleException.class, "Key 'y' already present in index.", r1);

    execute("update t3 set a = 'z' where a = 'x';");
    QueryResult r2 = query("select * from t3;");
    assertHeader(new String[] { "a", "b" }, r2);
    assertValues(new Object[][] {
        { "y", 2L },
        { "z", 1L } }, r2);
  }

  @Test
  public void testUpdateWithForeignKey() throws Exception {
    execute("create table t4 (a varchar, constraint pk_a primary key (a));");
    execute("create table t5 (a varchar, b varchar, constraint fk_b foreign key (b) references t4(a));");
    execute("insert into t4 values ('x');");
    execute("insert into t4 values ('y');");
    execute("insert into t5 values ('123', 'x');");

    ErrorResult r1 = error("update t4 set a = 'z' where a = 'x';");
    assertError(ModelException.class, "[14, 15]: Column 'admin.t4.a' is immutable.", r1);

    ErrorResult r2 = error("update t5 set b = 'z' where b = 'x';");
    assertError(RuleException.class, "Foreign key violation, value 'z' not present.", r2);

    execute("update t5 set b = 'y' where b = 'x';");
    QueryResult r3 = query("select * from t5;");
    assertHeader(new String[] { "a", "b" }, r3);
    assertValues(new Object[][] { { "123", "y" } }, r3);

    statement("delete from t4 where a = 'x';");
  }

  @Test
  public void testUpdateWithForeignKeyIndexes() throws Exception {
    execute("create table t6 (a varchar, constraint pk_a primary key (a));");
    execute("create table t7 (a varchar, b varchar, constraint fk_b foreign key (b) references t6(a));");
    execute("insert into t6 values ('x');");
    execute("insert into t6 values ('y');");
    execute("insert into t7 values ('123', 'x');");

    TableUniqueIndex t6a = metaRepo.table("admin", "t6").uniqueIndexes().get("a");
    assertEquals(0L, t6a.get("x")[0]);
    assertEquals(16L, t6a.get("y")[0]);
    TableMultiIndex t7b = metaRepo.table("admin", "t7").multiIndexes().get("b");
    org.junit.Assert.assertArrayEquals(new long[] { 0L }, t7b.get("x"));
    assertEquals(false, t7b.contains("y"));

    execute("update t7 set b = 'y' where b = 'x';");
    assertEquals(0L, t6a.get("x")[0]);
    assertEquals(16L, t6a.get("y")[0]);
    assertEquals(false, t7b.contains("x"));
    org.junit.Assert.assertArrayEquals(new long[] { 27L }, t7b.get("y"));
  }

  @Test
  public void testUpdateMultipleTimes() throws Exception {
    execute("create table t8 (a integer, b float);");
    execute("insert into t8 values (1, 1.0);");
    QueryResult r1 = query("select * from t8;");
    assertHeader(new String[] { "a", "b" }, r1);
    assertValues(new Object[][] { { 1L, 1.0 } }, r1);

    for (int i = 0; i < 10; i++) {
      execute("update t8 set a = a + 1, b = b + 1.0;");
    }
    QueryResult r2 = query("select * from t8;");
    assertValues(new Object[][] { { 11L, 11.0 } }, r2);
  }

  @Test
  public void testUpdateReferencedByForeignKey() throws Exception {
    execute("create table t9 (a integer, b integer, constraint pk_a primary key (a));");
    execute("create table t10 (a integer, constraint fk_a foreign key (a) references t9(a));");
    execute("insert into t9 values (1, 1);");
    execute("insert into t10 values (1);");
    QueryResult r1 = query("select a, fk_a.a as a2, fk_a.b as b2 from t10;");
    assertHeader(new String[] { "a", "a2", "b2" }, r1);
    assertValues(new Object[][] { { 1L, 1L, 1L } }, r1);
    TableUniqueIndex t9a = metaRepo.table("admin", "t9").uniqueIndexes().get("a");
    assertEquals(0L, t9a.get0(1L));
    TableMultiIndex t10a = metaRepo.table("admin", "t10").multiIndexes().get("a");
    assertEquals(0L, t10a.get(1L)[0]);

    execute("update t9 set b = 2;");
    QueryResult r2 = query("select a, fk_a.a as a2, fk_a.b as b2 from t10;");
    assertValues(new Object[][] { { 1L, 1L, 2L } }, r2);
    assertEquals(27L, t9a.get0(1L));
    assertEquals(0L, t10a.get(1L)[0]);
  }

  @Test
  public void testUpdateReferencedByRules() throws Exception {
    execute("create table t11 (a varchar, b integer, constraint pk_a primary key (a));");
    execute("create table t12 (a varchar, constraint fk_a foreign key (a) references t11(a),"
        + "constraint c_b check (fk_a.b > 0));");
    execute("insert into t11 values ('x', 1);");
    execute("insert into t12 values ('x');");

    QueryResult r1 = query("select a, fk_a.a as a2, fk_a.b as b2 from t12;");
    assertHeader(new String[] { "a", "a2", "b2" }, r1);
    assertValues(new Object[][] { { "x", "x", 1L } }, r1);

    execute("update t11 set b = 2;");
    QueryResult r2 = query("select a, fk_a.a as a2, fk_a.b as b2 from t12;");
    assertValues(new Object[][] { { "x", "x", 2L } }, r2);

    ErrorResult e = error("update t11 set b = 0;");
    assertEquals("Referencing constraint check t12.c_b failed.", e.getError().getMessage());
  }

  @Test
  public void testUpdateReferencedByRules_MultipleLevels() throws Exception {
    execute("create table t13 (a integer, b integer, constraint pk_a primary key (a));");
    execute("create table t14 (a integer, b integer, constraint pk_a primary key (a),"
        + "constraint fk_b foreign key (b) references t13(a));");
    execute("create table t15 (a integer, constraint fk_a foreign key (a) references t14(a),"
        + "constraint c_b check (fk_a.fk_b.b > 0));");
    execute("insert into t13 values (1, 1);");
    execute("insert into t14 values (1, 1);");
    execute("insert into t15 values (1);");

    execute("update t13 set b = 2;");
    QueryResult r1 = query("select a, fk_a.fk_b.b from t15;");
    assertValues(new Object[][] { { 1L, 2L } }, r1);

    ErrorResult e = error("update t13 set b = 0;");
    assertEquals("Referencing constraint check t15.c_b failed.", e.getError().getMessage());
  }

  @Test
  public void testUpdateReferencedByRules_MultipleLinks() throws Exception {
    execute("create table t16 (a integer, b integer, constraint pk_a primary key (a));");
    execute("create table t17 (a integer, b integer, "
        + "constraint fk_a foreign key (a) references t16(a), "
        + "constraint fk_b foreign key (b) references t16(a),"
        + "constraint c_a check (fk_a.b > 0),"
        + "constraint c_b check (fk_b.b > 0));");
    execute("insert into t16 values (1, 1), (2, 2);");
    execute("insert into t17 values (1, 2);");

    execute("update t16 set b = 3 where a = 1;");
    QueryResult r1 = query("select a, fk_a.b as b1, fk_b.b as b2 from t17;");
    assertValues(new Object[][] { { 1L, 3L, 2L } }, r1);

    execute("update t16 set b = 4 where a = 2;");
    QueryResult r2 = query("select a, fk_a.b as b1, fk_b.b as b2 from t17;");
    assertValues(new Object[][] { { 1L, 3L, 4L } }, r2);

    ErrorResult e1 = error("update t16 set b = 0 where a = 1;");
    assertEquals("Referencing constraint check t17.c_a failed.", e1.getError().getMessage());

    ErrorResult e2 = error("update t16 set b = 0 where a = 2;");
    assertEquals("Referencing constraint check t17.c_b failed.", e2.getError().getMessage());

    QueryResult r3 = query("select a, fk_a.b as b1, fk_b.b as b2 from t17;");
    assertValues(new Object[][] { { 1L, 3L, 4L } }, r3);
  }

  @Test
  public void testUpdateReferencedByRules_MultipleVariables() throws Exception {
    execute("create table t18 (a integer, b integer, c integer, constraint pk_a primary key (a));");
    execute("create table t19 (a integer, constraint fk_a foreign key (a) references t18(a), "
        + "constraint c_x check (fk_a.b + fk_a.c > 0));");
    execute("insert into t18 values (1, 1, 1);");
    execute("insert into t19 values (1);");

    execute("update t18 set b = 2;");
    QueryResult r1 = query("select a, fk_a.b, fk_a.c from t19;");
    assertValues(new Object[][] { { 1L, 2L, 1L } }, r1);

    execute("update t18 set c = 2;");
    QueryResult r2 = query("select a, fk_a.b, fk_a.c from t19;");
    assertValues(new Object[][] { { 1L, 2L, 2L } }, r2);

    ErrorResult e1 = error("update t18 set b = -2;");
    assertEquals("Referencing constraint check t19.c_x failed.", e1.getError().getMessage());

    ErrorResult e2 = error("update t18 set c = -2;");
    assertEquals("Referencing constraint check t19.c_x failed.", e2.getError().getMessage());

    QueryResult r3 = query("select a, fk_a.b, fk_a.c from t19;");
    assertValues(new Object[][] { { 1L, 2L, 2L } }, r3);
  }

  @Test
  public void testUpdateReferencedByRules_RuleRefersThirdTable() throws Exception {
    execute("create table t20 (a integer, b integer, constraint pk_a primary key (a));");
    execute("create table t21 (a integer, b integer, constraint pk_a primary key (a));");
    execute("create table t22 (c integer, d integer, "
        + "constraint fk_c foreign key (c) references t20(a), "
        + "constraint fk_d foreign key (d) references t21(a), "
        + "constraint c_x check (fk_c.b + fk_d.b > 0));");
    execute("insert into t20 values (1, 1);");
    execute("insert into t21 values (1, 1);");
    execute("insert into t22 values (1, 1);");

    execute("update t20 set b = 2;");
    QueryResult r1 = query("select fk_c.b as b1, fk_d.b as b2 from t22;");
    assertValues(new Object[][] { { 2L, 1L } }, r1);

    execute("update t21 set b = 2;");
    QueryResult r2 = query("select fk_c.b as b1, fk_d.b as b2 from t22;");
    assertValues(new Object[][] { { 2L, 2L } }, r2);

    ErrorResult e1 = error("update t20 set b = -2;");
    assertEquals("Referencing constraint check t22.c_x failed.", e1.getError().getMessage());

    ErrorResult e2 = error("update t21 set b = -2;");
    assertEquals("Referencing constraint check t22.c_x failed.", e2.getError().getMessage());

    QueryResult r3 = query("select fk_c.b as b1, fk_d.b as b2 from t22;");
    assertValues(new Object[][] { { 2L, 2L } }, r3);
  }

  @Test
  public void testUpdateReferencedByRules_MultiTable() throws Exception {
    execute("create table t23 (a varchar, constraint pk_a primary key (a));");
    execute("create table t24 (b varchar, c integer, constraint fk_a foreign key (b) references t23(a));");
    execute("alter table t23 add view s (select sum(c) as sc from rev_fk_a);");
    execute("alter table t23 add constraint c_c check (s.sc <= 4);");

    execute("insert into t23 values ('x');");
    execute("insert into t24 values ('x', 1);");
    execute("insert into t24 values ('x', 1);");

    execute("update t24 set c = 2;");
    QueryResult r1 = query("select b, c, fk_a.a from t24;");
    assertValues(new Object[][] { { "x", 2L, "x" }, { "x", 2L, "x" } }, r1);

    ErrorResult e1 = error("update t24 set c = 3;");
    assertEquals("Referencing constraint check t23.c_c failed.", e1.getError().getMessage());
    QueryResult r2 = query("select b, c, fk_a.a from t24;");
    assertValues(new Object[][] { { "x", 2L, "x" }, { "x", 2L, "x" } }, r2);
  }

  @Test
  public void testUpdateForeignKeyToNull() {
    execute("create table t25 (a1 varchar, b1 integer, constraint pk_a primary key (a1));");
    execute("create table t26 (a2 varchar, b2 varchar, constraint fk_a foreign key (a2) references t25(a1));");

    execute("insert into t25 values ('x', 1);");
    execute("insert into t26 values ('x', 'a');");

    QueryResult r1 = query("select a2, b2, fk_a.b1 from t26;");
    assertHeader(new String[] { "a2", "b2", "b1" }, r1);
    assertValues(new Object[][] { { "x", "a", 1L } }, r1);

    execute("update t26 set a2 = null;");

    QueryResult r2 = query("select a2, b2, fk_a.b1 from t26;");
    assertHeader(new String[] { "a2", "b2", "b1" }, r2);
    assertValues(new Object[][] { { null, "a", null } }, r2);
  }

  @Test
  public void testUpdateImmutableColumn() {
    execute("create table t27 (a varchar immutable, b varchar);");

    execute("insert into t27 values ('x', 'y');");

    QueryResult r1 = query("select a, b from t27;");
    assertHeader(new String[] { "a", "b" }, r1);
    assertValues(new Object[][] { { "x", "y" } }, r1);

    execute("update t27 set b = 'z';");

    QueryResult r2 = query("select a, b from t27;");
    assertHeader(new String[] { "a", "b" }, r2);
    assertValues(new Object[][] { { "x", "z" } }, r2);

    ErrorResult e1 = error("update t27 set a = 'z';");
    assertEquals("[15, 16]: Column 'admin.t27.a' is immutable.", e1.getError().getMessage());

    execute("alter table t27 drop a;");
    execute("alter table t27 add a varchar;");

    QueryResult r3 = query("select a, b from t27;");
    assertHeader(new String[] { "a", "b" }, r3);
    assertValues(new Object[][] { { null, "z" } }, r3);
  }

  @Test
  public void testUpdateWrongType() {
    execute("create table t28 (a varchar, b integer, c float, d timestamp, e boolean);");
    execute("insert into t28 values('x', 1, 1.0, dt '2017-01-01', true);");
    ErrorResult e1 = error("update t28 set a = 1;");
    assertError(ModelException.class, "[15, 16]: Expected 'varchar' but got 'integer' for 'a'.", e1);
    ErrorResult e2 = error("update t28 set b = 1.0;");
    assertError(ModelException.class, "[15, 16]: Expected 'integer' but got 'float' for 'b'.", e2);
    ErrorResult e3 = error("update t28 set c = 'x';");
    assertError(RuleException.class, "Invalid float 'x'.", e3);
    ErrorResult e4 = error("update t28 set d = 1;");
    assertError(ModelException.class, "[15, 16]: Expected 'timestamp' but got 'integer' for 'd'.", e4);
    ErrorResult e5 = error("update t28 set e = 'x';");
    assertError(RuleException.class, "Invalid boolean 'x'.", e5);
  }

  @Test
  public void testAutoGeneratedID() {
    execute("create table t29 (a id, b varchar, c integer);");
    execute("insert into t29 values ('x', 10), ('y', 10);");
    ErrorResult e1 = error("update t29 set a = 1;");
    assertError(ModelException.class, "[15, 16]: Column 'admin.t29.a' is immutable.", e1);
    execute("update t29 set c = 1;");
    QueryResult r1 = query("select * from t29;");
    assertValues(new Object[][] { { 0l, "x", 1l }, { 1l, "y", 1l } }, r1);
  }

  @Test
  public void testEnumType() {
    execute("create table t30 (a enum('x', 'y'));");
    execute("insert into t30 values ('x');");

    QueryResult r1 = query("select a from t30;");
    assertValues(new Object[][] { { "x" } }, r1);

    execute("update t30 set a = 'y';");
    QueryResult r2 = query("select a from t30;");
    assertValues(new Object[][] { { "y" } }, r2);

    ErrorResult e1 = error("update t30 set a = 'z';");
    assertError(RuleException.class, "Invalid enum value 'z'.", e1);
  }

  @Test
  public void testUpdateIDIndex() throws Exception {
    execute("create table t31 (a id, b varchar);");
    execute("insert into t31 values ('x'), ('y');");

    TableUniqueIndex index = metaRepo.table("admin", "t31").uniqueIndexes().get("a");
    assertEquals(0L, index.get0(0L));

    execute("update t31 set b = 'z' where a = 0;");
    assertEquals(50L, index.get0(0L));
    QueryResult r1 = query("select * from t31 where a = 0;");
    assertValues(new Object[][] { { 0L, "z" } }, r1);
  }

  @Test
  public void testUpdateForeignKeyFromNull() {
    execute("create table t32 (a1 varchar, b1 integer, constraint pk_a primary key (a1));");
    execute("create table t33 (a2 varchar, b2 varchar, constraint fk_a foreign key (a2) references t32(a1));");

    execute("insert into t32 values ('x', 1);");
    execute("insert into t33 (b2) values ('a');");

    QueryResult r1 = query("select a2, b2, fk_a.b1 from t33;");
    assertHeader(new String[] { "a2", "b2", "b1" }, r1);
    assertValues(new Object[][] { { null, "a", null } }, r1);

    execute("update t26 set a2 = 'x';");

    QueryResult r2 = query("select a2, b2, fk_a.b1 from t26;");
    assertHeader(new String[] { "a2", "b2", "b1" }, r2);
    assertValues(new Object[][] { { "x", "a", 1L } }, r2);
  }

  @Test
  public void testDateType() throws ParseException {
    SimpleDateFormat sdf = TableReaderTest.sdf;
    execute("create table t34 (a timestamp('yyyy/MM'));");
    execute("insert into t34 values ('2018/01');");

    execute("update t34 set a = dt '2018-01-01';");
    assertValues(new Object[][] { { sdf.parse("20180101") } }, query("select a from t34;"));

    execute("update t34 set a = '2018/02';");
    assertValues(new Object[][] { { sdf.parse("20180201") } }, query("select a from t34;"));

    ErrorResult e1 = error("update t34 set a = '201803';");
    assertError(RuleException.class, "Invalid timestamp '201803'.", e1);
  }
}
