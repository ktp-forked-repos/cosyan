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
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.ErrorResult;
import com.cosyan.db.lang.transaction.Result.QueryResult;

public class DropStatementTest extends UnitTestBase {

  @Test
  public void testDropTable() throws Exception {
    execute("create table t1 (a varchar);");
    metaRepo.table("admin", "t1");
    execute("drop table t1;");
    assertFalse(metaRepo.hasTable("t1", "admin"));
  }

  @Test
  public void testQueryDroppedTable() throws Exception {
    execute("create table t2 (a varchar);");
    execute("insert into t2 values('x');");
    QueryResult result = query("select * from t2;");
    assertHeader(new String[] { "a" }, result);
    assertValues(new Object[][] { { "x" } }, result);

    execute("drop table t2;");
    ErrorResult e = error("select * from t2;");
    assertEquals("[14, 16]: Table or view 'admin.t2' does not exist.", e.getError().getMessage());
  }

  @Test
  public void testCanNotDropTableWithReference() throws Exception {
    execute("create table t3 (a varchar, constraint pk_a primary key (a));");
    execute("create table t4 (a varchar, constraint fk_a foreign key (a) references t3(a));");

    ErrorResult e = error("drop table t3;");
    assertEquals("[11, 13]: Cannot drop table 'admin.t3', referenced by foreign key 'admin.t4.fk_a [a -> admin.t3.a]'.",
        e.getError().getMessage());
  }

  @Test
  public void testDropIndex() throws Exception {
    execute("create table t5 (a varchar);");
    execute("create index t5.a;");
    assertEquals(1, metaRepo.table("admin", "t5").multiIndexes().size());
    execute("drop index t5.a;");
    assertEquals(0, metaRepo.table("admin", "t5").multiIndexes().size());
  }

  @Test
  public void testDropIndexError() throws Exception {
    execute("create table t6 (a varchar unique, b varchar);");

    {
      ErrorResult e = error("drop index t6.a;");
      assertEquals("[14, 15]: Cannot drop index 'admin.t6.a', column is unique.", e.getError().getMessage());
    }
    execute("drop index t6.b;");
    {
      ErrorResult e = error("drop index t6.c;");
      assertEquals("[14, 15]: Column 'c' not found in table 'admin.t6'.", e.getError().getMessage());
    }
  }

  @Test
  public void testDropView() throws Exception {
    execute("create table t7 (a integer, b varchar);");
    execute("create view v8 as select sum(a) as sa, b from t7 group by b;");
    assertEquals("admin.v8", metaRepo.view("admin", "v8").fullName());
    execute("drop view v8;");
    assertFalse(metaRepo.hasView("admin", "v8"));
  }
}
