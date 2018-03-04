package com.cosyan.db.lang.sql;

import java.io.IOException;

import com.cosyan.db.lang.sql.CreateStatement.ConstraintDefinition;
import com.cosyan.db.lang.sql.Result.MetaStatementResult;
import com.cosyan.db.lang.sql.SyntaxTree.MetaStatement;
import com.cosyan.db.lang.sql.SyntaxTree.Node;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.Ident;
import com.cosyan.db.model.MaterializedTableMeta;
import com.google.common.collect.ImmutableList;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class AlterStatementConstraints {

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class AlterTableAddConstraint extends Node implements MetaStatement {
    private final Ident table;
    private final ConstraintDefinition constraint;

    @Override
    public Result execute(MetaRepo metaRepo) throws ModelException, IOException {
      MaterializedTableMeta tableMeta = metaRepo.table(table);
      CreateStatement.CreateTable.addConstraints(metaRepo, tableMeta, ImmutableList.of(constraint));
      return new MetaStatementResult();
    }
  }
}