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
package com.cosyan.db.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.cosyan.db.io.TableReader.IterableTableReader;
import com.cosyan.db.meta.Dependencies.TableDependencies;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.Aggregators.Aggregator;
import com.cosyan.db.model.ColumnMeta.AggrColumn;
import com.cosyan.db.model.ColumnMeta.IndexColumn;
import com.cosyan.db.model.DerivedTables.KeyValueTableMeta;
import com.cosyan.db.model.TableMeta.IterableTableMeta;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableList;

public abstract class AggrTables extends IterableTableMeta {

  public static class NotAggrTableException extends ModelException {
    private static final long serialVersionUID = 1L;

    public NotAggrTableException(Ident ident) {
      super(String.format("Not an aggregation table '%s'.", ident), ident);
    }
  }

  public abstract class AggrTableReader extends IterableTableReader {

    protected IterableTableReader sourceReader;
    protected Iterator<Object[]> iterator;
    protected boolean aggregated;

    public AggrTableReader(IterableTableReader sourceReader) {
      this.sourceReader = sourceReader;
    }

    @Override
    public void close() throws IOException {
      sourceReader.close();
    }
  }

  protected ColumnMeta havingColumn;
  protected final ArrayList<AggrColumn> aggrColumns;

  public AggrTables() {
    this.aggrColumns = new ArrayList<>();
    this.havingColumn = ColumnMeta.TRUE_COLUMN;
  }

  protected int size() {
    return aggrColumns.size() + sourceTable().getKeyColumns().size();
  }

  @Override
  protected TableMeta getRefTable(Ident ident) throws ModelException {
    return null;
  }

  @Override
  public MetaResources readResources() {
    MetaResources resources = sourceTable().readResources();
    for (AggrColumn column : aggrColumns) {
      resources = resources.merge(column.readResources());
    }
    return resources;
  }

  public abstract KeyValueTableMeta sourceTable();

  public void addAggrColumn(AggrColumn aggrColumn) {
    aggrColumns.add(aggrColumn);
  }

  public void setHavingColumn(ColumnMeta havingColumn) {
    this.havingColumn = havingColumn;
  }

  @Override
  public IndexColumn getColumn(Ident ident) throws ModelException {
    return sourceTable().column(ident).shift(this, 0);
  }

  @Override
  public ImmutableList<String> columnNames() {
    return sourceTable().columnNames();
  }

  public static class KeyValueAggrTableMeta extends AggrTables {
    private final KeyValueTableMeta sourceTable;

    public KeyValueAggrTableMeta(
        KeyValueTableMeta sourceTable) {
      this.sourceTable = sourceTable;
    }

    @Override
    public KeyValueTableMeta sourceTable() {
      return sourceTable;
    }

    @Override
    public Object[] values(Object[] key, Resources resources) throws IOException {
      IterableTableReader reader = reader(resources, TableContext.withParent(key));
      Object[] aggrValues = reader.next();
      reader.close();
      return aggrValues;
    }

    public IterableTableReader reader(Resources resources, TableContext context) throws IOException {
      return new AggrTableReader(sourceTable.reader(resources, context)) {
        @Override
        public Object[] next() throws IOException {
          if (!aggregated) {
            aggregate(resources, context);
          }
          Object[] values = null;
          do {
            if (!iterator.hasNext()) {
              return null;
            }
            values = iterator.next();
          } while (!(boolean) havingColumn.value(values, resources, context) && !cancelled.get());
          return values;
        }

        private ArrayList<Object> getKeyValues(Object[] sourceValues, Resources resources, TableContext context) throws IOException {
          ArrayList<Object> keys = new ArrayList<>(sourceTable.getKeyColumns().size());
          for (Map.Entry<String, ? extends ColumnMeta> entry : sourceTable.getKeyColumns().entrySet()) {
            keys.add(entry.getValue().value(sourceValues, resources, context));
          }
          return keys;
        }

        private void aggregate(Resources resources, TableContext context) throws IOException {
          HashMap<ArrayList<Object>, Aggregator<?, ?>[]> aggregatedValues = new HashMap<>();
          while (!cancelled.get()) {
            Object[] sourceValues = sourceReader.next();
            if (sourceValues == null) {
              break;
            }
            ArrayList<Object> keyValues = getKeyValues(sourceValues, resources, context);
            if (!aggregatedValues.containsKey(keyValues)) {
              Aggregator<?, ?>[] aggrValues = new Aggregator[aggrColumns.size()];
              int i = 0;
              for (AggrColumn column : aggrColumns) {
                aggrValues[i++] = column.getFunction().create();
              }
              aggregatedValues.put(keyValues, aggrValues);
            }
            Aggregator<?, ?>[] aggrValues = aggregatedValues.get(keyValues);
            int i = 0;
            for (AggrColumn column : aggrColumns) {
              aggrValues[i++].add(column.getInnerValue(sourceValues, resources, context));
            }
          }
          final Iterator<Entry<ArrayList<Object>, Aggregator<?, ?>[]>> innerIterator = aggregatedValues.entrySet()
              .iterator();
          iterator = new Iterator<Object[]>() {

            @Override
            public boolean hasNext() {
              return innerIterator.hasNext();
            }

            @Override
            public Object[] next() {
              Object[] result = new Object[size()];
              Entry<ArrayList<Object>, Aggregator<?, ?>[]> item = innerIterator.next();
              Object[] keys = item.getKey().toArray();
              System.arraycopy(keys, 0, result, 0, keys.length);
              for (int i = 0; i < item.getValue().length; i++) {
                result[keys.length + i] = item.getValue()[i].finish();
              }
              return result;
            }
          };
          aggregated = true;
        }
      };
    }
  }

  public static class GlobalAggrTableMeta extends AggrTables {
    private final KeyValueTableMeta sourceTable;

    public GlobalAggrTableMeta(
        KeyValueTableMeta sourceTable) {
      this.sourceTable = sourceTable;
    }

    @Override
    public KeyValueTableMeta sourceTable() {
      return sourceTable;
    }

    @Override
    public Object[] values(Object[] key, Resources resources) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public IterableTableReader reader(Resources resources, TableContext context) throws IOException {
      return new AggrTableReader(sourceTable.reader(resources, context)) {

        @Override
        public Object[] next() throws IOException {
          if (!aggregated) {
            aggregate();
          }
          Object[] values = null;
          do {
            if (!iterator.hasNext()) {
              return null;
            }
            values = iterator.next();
          } while (!(boolean) havingColumn.value(values, resources, context) && !cancelled.get());
          return values;
        }

        protected void aggregate() throws IOException {
          Aggregator<?, ?>[] aggrValues = new Aggregator[size()];
          int i = 1;
          for (AggrColumn column : aggrColumns) {
            aggrValues[i++] = column.getFunction().create();
          }
          while (!cancelled.get()) {
            Object[] sourceValues = sourceReader.next();
            if (sourceValues == null) {
              break;
            }
            i = 1;
            for (AggrColumn column : aggrColumns) {
              aggrValues[i++].add(column.getInnerValue(sourceValues, resources, context));
            }
          }
          Object[] result = new Object[size()];
          for (int j = 0; j < aggrColumns.size(); j++) {
            result[j + 1] = aggrValues[j + 1].finish();
          }

          iterator = ImmutableList.of(result).iterator();
          aggregated = true;
        }
      };
    }
  }

  public int numAggrColumns() {
    return aggrColumns.size();
  }

  @Override
  public TableDependencies tableDependencies() {
    return sourceTable().tableDependencies();
  }
}
