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
package com.cosyan.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;

import com.cosyan.db.auth.AuthToken;
import com.cosyan.db.conf.Config;
import com.cosyan.db.lang.transaction.Result;
import com.cosyan.db.lang.transaction.Result.CrashResult;
import com.cosyan.db.lang.transaction.Result.ErrorResult;
import com.cosyan.db.lang.transaction.Result.MetaStatementResult;
import com.cosyan.db.lang.transaction.Result.QueryResult;
import com.cosyan.db.lang.transaction.Result.StatementResult;
import com.cosyan.db.lang.transaction.Result.TransactionResult;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.session.Session;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class UnitTestBase {
  protected static Session session;

  protected static Config config;
  protected static DBApi dbApi;
  protected static MetaRepo metaRepo;
  protected static AuthToken token;

  @BeforeClass
  public static void setUp() throws Exception {
    FileUtils.forceMkdir(new File("/tmp/data"));
    FileUtils.cleanDirectory(new File("/tmp/data"));
    FileUtils.copyFile(new File("src/test/resources/cosyan.db.properties"), new File("/tmp/data/cosyan.db.properties"));
    FileUtils.copyFile(new File("conf/users"), new File("/tmp/data/users"));
    config = new Config("/tmp/data");
    dbApi = new DBApi(config);
    metaRepo = dbApi.getMetaRepo();
    session = dbApi.newAdminSession();
    token = session.authToken();
  }

  protected static void execute(String sql) {
    Result result = session.execute(sql);
    if (result instanceof ErrorResult) {
      ((ErrorResult) result).getError().printStackTrace();
      fail(sql);
    }
    if (result instanceof CrashResult) {
      ((CrashResult) result).getError().printStackTrace();
      fail(sql);
    }
  }

  protected StatementResult statement(String sql) {
    return statement(sql, session);
  }

  protected MetaStatementResult metaStatement(String sql, Session session) {
    Result result = session.execute(sql);
    if (result instanceof ErrorResult) {
      ((ErrorResult) result).getError().printStackTrace();
      fail(sql);
    }
    if (result instanceof CrashResult) {
      ((CrashResult) result).getError().printStackTrace();
      fail(sql);
    }
    return (MetaStatementResult) result;
  }

  protected StatementResult statement(String sql, Session session) {
    Result result = session.execute(sql);
    if (result instanceof ErrorResult) {
      ((ErrorResult) result).getError().printStackTrace();
      fail(sql);
    }
    if (result instanceof CrashResult) {
      ((CrashResult) result).getError().printStackTrace();
      fail(sql);
    }
    return (StatementResult) Iterables.getOnlyElement(((TransactionResult) result).getResults());
  }

  protected ErrorResult error(String sql) {
    Result result = session.execute(sql);
    if (result instanceof CrashResult) {
      ((CrashResult) result).getError().printStackTrace();
      fail(sql);
    }
    if (result instanceof TransactionResult) {
      System.err.println("Query '" + sql + "' did not produce an error.");
      fail(sql);
    }
    return (ErrorResult) result;
  }

  public static QueryResult query(String sql, Session session) {
    Result result = session.execute(sql);
    if (result instanceof TransactionResult) {
      return (QueryResult) Iterables.getOnlyElement(((TransactionResult) result).getResults());
    } else if (result instanceof ErrorResult) {
      ErrorResult crash = (ErrorResult) result;
      crash.getError().printStackTrace();
      throw new RuntimeException(crash.getError());
    } else {
      CrashResult crash = (CrashResult) result;
      crash.getError().printStackTrace();
      throw new RuntimeException(crash.getError());
    }
  }

  public static TransactionResult transaction(String sql) {
    Result result = session.execute(sql);
    if (result instanceof CrashResult) {
      CrashResult crash = (CrashResult) result;
      crash.getError().printStackTrace();
      throw new RuntimeException(crash.getError());
    }
    return (TransactionResult) result;
  }

  protected QueryResult query(String sql) {
    return query(sql, session);
  }

  public static StatementResult stmt(String sql, Session session) {
    Result result = session.execute(sql);
    if (result instanceof TransactionResult) {
      return (StatementResult) Iterables.getOnlyElement(((TransactionResult) result).getResults());
    } else if (result instanceof ErrorResult) {
      ErrorResult crash = (ErrorResult) result;
      crash.getError().printStackTrace();
      throw new RuntimeException(crash.getError());
    } else {
      CrashResult crash = (CrashResult) result;
      crash.getError().printStackTrace();
      throw new RuntimeException(crash.getError());
    }
  }

  public static StatementResult stmt(String sql) {
    return stmt(sql, session);
  }

  protected void assertHeader(String[] expected, QueryResult result) {
    assertEquals(ImmutableList.copyOf(expected), result.getHeader());
  }

  protected void assertValues(Object[][] expected, QueryResult result) {
    assertEquals("Wrong row number:", expected.length, result.getValues().size());
    for (int i = 0; i < expected.length; i++) {
      for (int j = 0; j < expected[i].length; j++) {
        assertEquals(String.format("%s:%s", i, j), expected[i][j], result.getValues().get(i)[j]);
      }
    }
  }

  protected void assertError(Class<? extends Exception> clss, String message, ErrorResult result) {
    assertEquals(clss, result.getError().getClass());
    assertEquals(message, result.getError().getMessage());
  }

  protected String speed(long t, long n) {
    return String.format("(%.2f records per sec)", (n * 1000.0) / t);
  }
}
