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
package com.cosyan.ui.entity;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.cosyan.db.DBApi;
import com.cosyan.db.entity.EntityHandler;
import com.cosyan.db.session.Session;
import com.cosyan.ui.ParamServlet;
import com.cosyan.ui.SessionHandler;
import com.cosyan.ui.ParamServlet.Servlet;

@Servlet(path = "loadEntity", doc = "Returns the metadata for all entities.")
public class EntityLoadServlet extends ParamServlet {
  private static final long serialVersionUID = 1L;

  private final SessionHandler sessionHandler;
  private final EntityHandler entityHandler;

  public EntityLoadServlet(DBApi dbApi, SessionHandler sessionHandler) {
    this.sessionHandler = sessionHandler;
    this.entityHandler = dbApi.entityHandler();
  }

  @Param(name = "token", doc = "User authentication token.")
  @Param(name = "session", doc = "Session ID.")
  @Param(name = "table", mandatory = true, doc = "Full table name.")
  @Param(name = "id", mandatory = true, doc = "Value of the ID column of the table.")
  @Override
  protected void doGetImpl(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    sessionHandler.execute(req, resp, (Session session) -> {
      String table = req.getParameter("table");
      String id = req.getParameter("id");
      return entityHandler.loadEntity(table, id, session).toJSON();
    });
  }
}
