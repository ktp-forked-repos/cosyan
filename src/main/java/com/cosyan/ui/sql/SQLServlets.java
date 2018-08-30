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
package com.cosyan.ui.sql;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.cosyan.db.session.Session;
import com.cosyan.ui.ParamServlet;
import com.cosyan.ui.SessionHandler;

public class SQLServlets {
  public static class SQLServlet extends ParamServlet {
    private static final long serialVersionUID = 1L;

    private final SessionHandler sessionHandler;

    public SQLServlet(SessionHandler sessionHandler) {
      this.sessionHandler = sessionHandler;
    }

    @Param(name = "token")
    @Param(name = "session")
    @Param(name = "sql", mandatory = true)
    @Override
    protected void doGetImpl(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      sessionHandler.execute(req, resp, (Session session) -> {
        String sql = req.getParameter("sql");
        return session.execute(sql).toJSON();
      });
    }
  }

  public static class CancelServlet extends ParamServlet {
    private static final long serialVersionUID = 1L;

    private final SessionHandler sessionHandler;

    public CancelServlet(SessionHandler sessionHandler) {
      this.sessionHandler = sessionHandler;
    }

    @Param(name = "token")
    @Param(name = "session")
    @Override
    protected void doGetImpl(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      sessionHandler.cancel(req, resp);
    }
  }
}