package com.cosyan.db.lang.expr;

import java.io.IOException;

import com.cosyan.db.auth.AuthToken;
import com.cosyan.db.lang.sql.Tokens.Loc;
import com.cosyan.db.lang.transaction.Result;
import com.cosyan.db.meta.Grants.GrantException;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.meta.MetaRepo.RuleException;
import com.cosyan.db.meta.MetaRepoExecutor;
import com.cosyan.db.model.DataTypes.DataType;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class SyntaxTree {

  public static enum AggregationExpression {
    YES, NO, EITHER
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static abstract class Node {

  }

  public static abstract class Statement {

    public abstract MetaResources compile(MetaRepo metaRepo) throws ModelException;

    public abstract Result execute(Resources resources) throws RuleException, IOException;

    public abstract void cancel();
  }

  public static abstract class MetaStatement {

    public abstract boolean log();
  }

  public static abstract class AlterStatement extends MetaStatement {

    public abstract MetaResources executeMeta(MetaRepo metaRepo, AuthToken authToken)
        throws ModelException, GrantException, IOException;

    public abstract Result executeData(MetaRepoExecutor metaRepo, Resources resources)
        throws RuleException, IOException;

    public abstract void cancel();
  }

  public static abstract class GlobalStatement extends MetaStatement {

    public abstract Result execute(MetaRepo metaRepo, AuthToken authToken)
        throws ModelException, GrantException, IOException;
  }

  public static void assertType(DataType<?> expectedType, DataType<?> dataType, Loc loc) throws ModelException {
    if (!expectedType.javaClass().equals(dataType.javaClass())) {
      throw new ModelException(
          "Data type " + dataType + " did not match expected type " + expectedType + ".", loc);
    }
  }
}
