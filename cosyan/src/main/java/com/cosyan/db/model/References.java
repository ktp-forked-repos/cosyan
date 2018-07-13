package com.cosyan.db.model;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import com.cosyan.db.io.Indexes.IndexReader;
import com.cosyan.db.io.TableReader.IterableTableReader;
import com.cosyan.db.io.TableReader.MultiFilteredTableReader;
import com.cosyan.db.io.TableReader.SeekableTableReader;
import com.cosyan.db.meta.Dependencies.TableDependencies;
import com.cosyan.db.meta.MaterializedTable;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.meta.TableProvider;
import com.cosyan.db.model.AggrTables.GlobalAggrTableMeta;
import com.cosyan.db.model.ColumnMeta.IndexColumn;
import com.cosyan.db.model.DataTypes.DataType;
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
    public Object[] values(Object[] sourceValues, Resources resources, TableContext context) throws IOException;

    public TableMeta parent();
  }

  public static TableMeta getRefTable(
      ReferencedTable parent,
      String tableName,
      Ident key,
      Map<String, ForeignKey> foreignKeys,
      Map<String, ReverseForeignKey> reverseForeignKeys,
      Map<String, TableRef> refs) throws ModelException {
    String name = key.getString();
    if (foreignKeys.containsKey(name)) {
      return new ReferencedSimpleTableMeta(parent, foreignKeys.get(name));
    } else if (refs.containsKey(name)) {
      return new ReferencedRefTableMeta(parent, refs.get(name).getTableMeta());
    }
    throw new ModelException(String.format("Reference '%s' not found in table '%s'.", key, tableName), key);
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedRefTableMeta extends TableMeta implements ReferencedTable {

    private final ReferencedTable parent;
    private final AggRefTableMeta refTable;

    @Override
    public ImmutableList<String> columnNames() {
      return refTable.columnNames();
    }

    @Override
    public Object[] values(Object[] sourceValues, Resources resources, TableContext context) throws IOException {
      return refTable.values(parent.values(sourceValues, resources, context), resources, context);
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

    @Override
    public TableMeta parent() {
      return parent.parent();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedSimpleTableMeta extends TableMeta implements ReferencedTable, TableProvider {

    private final ReferencedTable parent;
    private final ForeignKey foreignKey;
    private final Object[] nulls;

    public ReferencedSimpleTableMeta(ReferencedTable parent, ForeignKey foreignKey) {
      this.parent = parent;
      this.foreignKey = foreignKey;
      nulls = new Object[foreignKey.getRefTable().columns().size()];
      Arrays.fill(nulls, null);
    }

    @Override
    public ImmutableList<String> columnNames() {
      return foreignKey.getRefTable().columnNames();
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
          foreignKey.getRefTable().tableName(),
          ident,
          foreignKey.getRefTable().foreignKeys(),
          foreignKey.getRefTable().reverseForeignKeys(),
          foreignKey.getRefTable().refs());
    }

    @Override
    public MetaResources readResources() {
      return parent.readResources().merge(MetaResources.readTable(foreignKey.getRefTable()));
    }

    @Override
    public Object[] values(Object[] sourceValues, Resources resources, TableContext context) throws IOException {
      Object[] parentValues = parent.values(sourceValues, resources, context);
      Object key = parentValues[foreignKey.getColumn().getIndex()];
      if (key == null) {
        return nulls;
      } else {
        SeekableTableReader reader = resources.reader(foreignKey.getRefTable().tableName());
        return reader.get(key, resources).getValues();
      }
    }

    @Override
    public TableMeta tableMeta(Ident ident) throws ModelException {
      if (foreignKey.getRefTable().reverseForeignKeys().containsKey(ident.getString())) {
        return new ReferencedMultiTableMeta(this, foreignKey.getRefTable().reverseForeignKey(ident));
      } else {
        throw new ModelException(String.format("Table '%s' not found.", ident.getString()), ident);
      }
    }

    @Override
    public TableProvider tableProvider(Ident ident) throws ModelException {
      if (foreignKey.getRefTable().foreignKeys().containsKey(ident.getString())) {
        return new ReferencedSimpleTableMeta(this, foreignKey.getRefTable().foreignKey(ident));
      } else {
        throw new ModelException(String.format("Table '%s' not found.", ident.getString()), ident);
      }
    }

    @Override
    public TableMeta parent() {
      return parent.parent();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ReferencedMultiTableMeta extends ExposedTableMeta implements ReferencedTable {

    private final ReferencedTable parent;
    private final ReverseForeignKey reverseForeignKey;
    private final MaterializedTable sourceTable;

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
          ident,
          reverseForeignKey.getRefTable().foreignKeys(),
          reverseForeignKey.getRefTable().reverseForeignKeys(),
          reverseForeignKey.getRefTable().refs());
    }

    @Override
    public MetaResources readResources() {
      return parent.readResources().merge(MetaResources.readTable(reverseForeignKey.getRefTable()));
    }

    @Override
    public IterableTableReader reader(final Object key, Resources resources, TableContext context) throws IOException {
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

    @Override
    public ImmutableList<DataType<?>> columnTypes() {
      return sourceTable.columnTypes();
    }

    @Override
    public TableMeta parent() {
      return parent.parent();
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class AggRefTableMeta extends TableMeta {
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
    public Object[] values(Object[] sourceValues, Resources resources, TableContext context) throws IOException {
      Object key = sourceValues[reverseForeignKey.getColumn().getIndex()];
      IterableTableReader reader = sourceTable.reader(key, resources, context);
      Object[] aggrValues = reader.next();
      reader.close();
      Object[] values = new Object[columns.size()];
      int i = 0;
      for (Map.Entry<String, ? extends ColumnMeta> entry : columns.entrySet()) {
        values[i++] = entry.getValue().value(aggrValues, resources, context);
      }
      return values;
    }
  }
}
