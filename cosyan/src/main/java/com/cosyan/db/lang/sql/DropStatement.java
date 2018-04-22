package com.cosyan.db.lang.sql;

import java.io.IOException;

import com.cosyan.db.auth.AuthToken;
import com.cosyan.db.lang.expr.SyntaxTree.MetaStatement;
import com.cosyan.db.lang.expr.SyntaxTree.Node;
import com.cosyan.db.lang.transaction.Result;
import com.cosyan.db.lang.transaction.Result.MetaStatementResult;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.BasicColumn;
import com.cosyan.db.model.Ident;
import com.cosyan.db.model.Keys.ReverseForeignKey;
import com.cosyan.db.model.MaterializedTableMeta;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class DropStatement {

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class DropTable extends Node implements MetaStatement {
    private final Ident table;

    @Override
    public Result execute(MetaRepo metaRepo, AuthToken authToken) throws ModelException, IOException {
      MaterializedTableMeta tableMeta = metaRepo.table(table);
      if (!tableMeta.reverseForeignKeys().isEmpty()) {
        ReverseForeignKey foreignKey = tableMeta.reverseForeignKeys().values().iterator().next();
        throw new ModelException(String.format("Cannot drop table '%s', referenced by foreign key '%s.%s'.",
            table.getString(),
            foreignKey.getRefTable().tableName(),
            foreignKey.getReverse()),
            table);
      }
      metaRepo.dropTable(table.getString());
      return new MetaStatementResult();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class DropIndex extends Node implements MetaStatement {
    private final Ident table;
    private final Ident column;

    @Override
    public Result execute(MetaRepo metaRepo, AuthToken authToken) throws ModelException, IOException {
      MaterializedTableMeta tableMeta = metaRepo.table(table);
      BasicColumn column = tableMeta.column(this.column);
      column.dropIndex(tableMeta, metaRepo);
      return new MetaStatementResult();
    }
  }
}
