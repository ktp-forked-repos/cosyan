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

import java.util.Random;

import org.junit.Test;

import com.cosyan.db.UnitTestBase;
import com.cosyan.db.lang.transaction.Result.StatementResult;

public class UpdateStatementPerformanceTest extends UnitTestBase {

  private static final int N = 5000;

  @Test
  public void testUpdateWithWhereIndexed() {
    long t = System.currentTimeMillis();
    execute("create table t1 (a varchar unique, b integer);");
    for (int i = 0; i < N; i++) {
      execute("insert into t1 values ('abc" + i + "' ," + i + ");");
    }
    t = System.currentTimeMillis() - t;
    System.out.println("Records with index inserted in " + t + " " + speed(t, N));
    t = System.currentTimeMillis();
    Random random = new Random();
    int updated = 0;
    for (int i = 0; i < N; i++) {
      int r = random.nextInt(N);
      StatementResult result = stmt("update t1 set b = b * 2 where a = 'abc" + r + "';");
      updated += result.getAffectedLines();
    }
    assertEquals(N, updated);
    t = System.currentTimeMillis() - t;
    System.out.println("Records with index updated in " + t + " " + speed(t, N));
  }

  @Test
  public void testUpdateWithWhereNotIndexed() {
    long t = System.currentTimeMillis();
    execute("create table t2 (a varchar, b integer);");
    for (int i = 0; i < N; i++) {
      execute("insert into t2 values ('abc" + i + "' ," + i + ");");
    }
    t = System.currentTimeMillis() - t;
    System.out.println("Records without index inserted in " + t + " " + speed(t, N));
    t = System.currentTimeMillis();
    Random random = new Random();
    int updated = 0;
    for (int i = 0; i < N; i++) {
      int r = random.nextInt(N);
      StatementResult result = stmt("update t2 set b = b * 2 where a = 'abc" + r + "';");
      updated += result.getAffectedLines();
    }
    assertEquals(N, updated);
    t = System.currentTimeMillis() - t;
    System.out.println("Records without index updated in " + t + " " + speed(t, N));
  }
}
