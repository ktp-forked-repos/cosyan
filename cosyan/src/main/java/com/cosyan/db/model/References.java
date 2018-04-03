package com.cosyan.db.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import com.cosyan.db.io.Indexes.IndexReader;
import com.cosyan.db.io.TableReader.IterableTableReader;
import com.cosyan.db.io.TableReader.MultiFilteredTableReader;
import com.cosyan.db.io.TableReader.SeekableTableReader;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.AggrTables.GlobalAggrTableMeta;
import com.cosyan.db.model.ColumnMeta.IndexColumn;
import com.cosyan.db.model.Dependencies.TableDependencies;
import com.cosyan.db.model.Keys.ForeignKey;
import com.cosyan.db.model.Keys.Ref;
import com.cosyan.db.model.Keys.ReverseForeignKey;
import com.cosyan.db.model.TableMeta.ExposedTableMeta;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class References {

  /**
   * A table referenced form another table through a chain of foreign keys or
   * reverse foreign keys. A chain of references uniquely identifies a
   * ReferencedTable.
   * 
   * @author gsvigruha
   */
  public static interface ReferencedTable {

    /**
     * The chain of references (foreign keys or reverse foreign keys) leading to
     * this table.
     */
    public Iterable<Ref> foreignKeyChain();

    /**
     * Returns all transitive read resources needed to for this table.
     */
    public MetaResources readResources();

    /**
     * Returns the referenced values based on sourceValues.
     */
    public Object[] values(Object[] sourceValues, Resources resources) throws IOException;
  }

  public static TableMeta getRefTable(
      ReferencedTable parent,
      String tableName,
      String key,
      Map<String, ForeignKey> foreignKeys,
      Map<String, ReverseForeignKey> reverseForeignKeys,
      Map<String, TableRef> refs) throws ModelException {
    if (foreignKeys.containsKey(key)) {
      return new ReferencedSimpleTableMeta(parent, foreignKeys.get(key));
    } else if (refs.containsKey(key)) {
      return new ReferencedRefTableMeta(parent, refs.get(key).getTableMeta());
    }
    throw new ModelException(String.format("Reference '%s' not found in table '%s'.", key, tableName));
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedRefTableMeta extends TableMeta implements ReferencedTable {

    private final ReferencedTable parent;
    private final RefTableMeta refTable;

    @Override
    public Object[] values(Object[] sourceValues, Resources resources) throws IOException {
      return refTable.values(parent.values(sourceValues, resources), resources);
    }

    @Override
    protected IndexColumn getColumn(Ident ident) throws ModelException {
      IndexColumn column = refTable.column(ident);
      TableDependencies deps = new TableDependencies(this, column.tableDependencies());
      return new IndexColumn(this, column.index(), column.getType(), deps);
    }

    @Override
    protected TableMeta getRefTable(Ident ident) throws ModelException {
      return refTable.getRefTable(ident);
    }

    @Override
    public MetaResources readResources() {
      return refTable.readResources();
    }

    @Override
    public Iterable<Ref> foreignKeyChain() {
      return parent.foreignKeyChain();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedSimpleTableMeta extends TableMeta implements ReferencedTable {

    private final ReferencedTable parent;
    private final ForeignKey foreignKey;

    public ReferencedSimpleTableMeta(ReferencedTable parent, ForeignKey foreignKey) {
      this.parent = parent;
      this.foreignKey = foreignKey;
    }

    @Override
    public Iterable<Ref> foreignKeyChain() {
      return ImmutableList.<Ref>builder().addAll(parent.foreignKeyChain()).add(foreignKey).build();
    }

    @Override
    protected IndexColumn getColumn(Ident ident) throws ModelException {
      BasicColumn column = foreignKey.getRefTable().column(ident);
      if (column == null) {
        return null;
      }
      TableDependencies deps = new TableDependencies();
      deps.addTableDependency(this);
      return new IndexColumn(this, column.getIndex(), column.getType(), deps);
    }

    @Override
    protected TableMeta getRefTable(Ident ident) throws ModelException {
      return References.getRefTable(
          this,
          foreignKey.getTable().tableName(),
          ident.getString(),
          foreignKey.getRefTable().foreignKeys(),
          foreignKey.getRefTable().reverseForeignKeys(),
          foreignKey.getRefTable().refs());
    }

    @Override
    public MetaResources readResources() {
      return parent.readResources().merge(MetaResources.readTable(foreignKey.getRefTable()));
    }

    @Override
    public Object[] values(Object[] sourceValues, Resources resources) throws IOException {
      Object[] parentValues = parent.values(sourceValues, resources);
      Object key = parentValues[foreignKey.getColumn().getIndex()];
      if (key == DataTypes.NULL) {
        Object[] values = new Object[foreignKey.getRefTable().columns().size()];
        Arrays.fill(values, DataTypes.NULL);
        return values;
      } else {
        SeekableTableReader reader = resources.reader(foreignKey.getRefTable().tableName());
        return reader.get(key, resources).getValues();
      }
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedMultiTableMeta extends ExposedTableMeta implements ReferencedTable {

    private final ReferencedTable parent;
    private final ReverseForeignKey reverseForeignKey;
    private final MaterializedTableMeta sourceTable;

    public ReferencedMultiTableMeta(ReferencedTable parent, ReverseForeignKey reverseForeignKey) {
      this.parent = parent;
      this.reverseForeignKey = reverseForeignKey;
      this.sourceTable = getReverseForeignKey().getRefTable();
    }

    @Override
    public Iterable<Ref> foreignKeyChain() {
      return ImmutableList.<Ref>builder().addAll(parent.foreignKeyChain()).add(reverseForeignKey).build();
    }

    @Override
    protected IndexColumn getColumn(Ident ident) throws ModelException {
      BasicColumn column = reverseForeignKey.getRefTable().column(ident);
      if (column == null) {
        return null;
      }
      TableDependencies deps = new TableDependencies();
      deps.addTableDependency(this);
      return new IndexColumn(this, column.getIndex(), column.getType(), deps);
    }

    @Override
    protected TableMeta getRefTable(Ident ident) throws ModelException {
      return References.getRefTable(
          this,
          reverseForeignKey.getTable().tableName(),
          ident.getString(),
          reverseForeignKey.getRefTable().foreignKeys(),
          reverseForeignKey.getRefTable().reverseForeignKeys(),
          reverseForeignKey.getRefTable().refs());
    }

    @Override
    public MetaResources readResources() {
      return parent.readResources().merge(MetaResources.readTable(reverseForeignKey.getRefTable()));
    }

    @Override
    public IterableTableReader reader(final Object key, Resources resources) throws IOException {
      String table = reverseForeignKey.getRefTable().tableName();
      final IndexReader index = resources.getIndex(reverseForeignKey);
      return new MultiFilteredTableReader(resources.reader(table), ColumnMeta.TRUE_COLUMN, resources) {

        @Override
        protected void readPositions() throws IOException {
          positions = index.get(key);
        }
      };
    }

    @Override
    public ImmutableList<String> columnNames() {
      return sourceTable.columnNames();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class RefTableMeta extends TableMeta {
    private final GlobalAggrTableMeta sourceTable;
    private final ImmutableMap<String, ColumnMeta> columns;
    private final ReverseForeignKey reverseForeignKey;

    @Override
    protected IndexColumn getColumn(Ident ident) throws ModelException {
      ColumnMeta column = columns.get(ident.getString());
      TableDependencies deps = new TableDependencies(this, column.tableDependencies());
      return new IndexColumn(sourceTable, indexOf(columns.keySet(), ident), column.getType(), deps);
    }

    @Override
    protected TableMeta getRefTable(Ident ident) throws ModelException {
      // Cannot reference any further tables from a ref, only access its fields.
      return null;
    }

    @Override
    public MetaResources readResources() {
      return sourceTable.readResources().merge(MetaResources.readTable(reverseForeignKey.getRefTable()));
    }

    public ImmutableList<String> columnNames() {
      return columns.keySet().asList();
    }

    @Override
    public Object[] values(Object[] sourceValues, Resources resources) throws IOException {
      Object key = sourceValues[reverseForeignKey.getColumn().getIndex()];
      IterableTableReader reader = sourceTable.reader(key, resources);
      Object[] aggrValues = reader.next();
      reader.close();
      Object[] values = new Object[columns.size()];
      int i = 0;
      for (Map.Entry<String, ? extends ColumnMeta> entry : columns.entrySet()) {
        values[i++] = entry.getValue().value(aggrValues, resources);
      }
      return values;
    }
  }
}
