package com.cosyan.db.transaction;

import java.io.IOException;

import com.cosyan.db.index.ByteTrie.IndexException;
import com.cosyan.db.logging.TransactionJournal;
import com.cosyan.db.meta.MetaRepo;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.sql.Result;
import com.cosyan.db.sql.Result.ErrorResult;
import com.cosyan.db.sql.Result.MetaStatementResult;
import com.cosyan.db.sql.SyntaxTree.MetaStatement;

public class MetaTransaction {
  
  private final long trxNumber;
  private final MetaStatement metaStatement;

  public MetaTransaction(long trxNumber, MetaStatement metaStatement) {
    this.trxNumber = trxNumber;
    this.metaStatement = metaStatement;
  }

  public long getTrxNumber() {
    return trxNumber;
  }

  public Result execute(MetaRepo metaRepo, TransactionJournal journal) {
    try {
      metaRepo.metaRepoWriteLock();
      metaStatement.execute(metaRepo);
      return new MetaStatementResult();
    } catch (ModelException | IndexException | IOException e) {
      return new ErrorResult(e);
    } finally {
      metaRepo.metaRepoWriteUnlock();
    }
  }
}
