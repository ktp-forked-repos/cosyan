package com.cosyan.db.model;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cosyan.db.io.TableReader.SeekableTableReader;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.ColumnMeta.BasicColumn;
import com.cosyan.db.model.ColumnMeta.DerivedColumn;
import com.cosyan.db.model.Keys.ForeignKey;
import com.cosyan.db.model.Keys.PrimaryKey;
import com.cosyan.db.model.TableMeta.ExposedTableMeta;
import com.cosyan.db.sql.CreateStatement.SimpleCheckDefinition;
import com.cosyan.db.sql.SyntaxTree.Ident;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class MaterializedTableMeta extends ExposedTableMeta {

  private final String tableName;
  private final LinkedHashMap<String, BasicColumn> columns;
  private final List<SimpleCheckDefinition> simpleCheckDefinitions;
  private final Map<String, DerivedColumn> simpleChecks;
  private Optional<PrimaryKey> primaryKey;
  private final Map<String, ForeignKey> foreignKeys;
  private final Map<String, ForeignKey> reverseForeignKeys;

  public MaterializedTableMeta(
      String tableName,
      LinkedHashMap<String, BasicColumn> columns,
      List<SimpleCheckDefinition> simpleCheckDefinitions,
      Map<String, DerivedColumn> simpleChecks,
      Optional<PrimaryKey> primaryKey,
      Map<String, ForeignKey> foreignKeys) {
    this.tableName = tableName;
    this.columns = columns;
    this.simpleCheckDefinitions = simpleCheckDefinitions;
    this.simpleChecks = simpleChecks;
    this.primaryKey = primaryKey;
    this.foreignKeys = foreignKeys;
    this.reverseForeignKeys = new HashMap<>();
  }

  public MaterializedTableMeta(
      String tableName,
      LinkedHashMap<String, BasicColumn> columns,
      List<SimpleCheckDefinition> simpleCheckDefinitions,
      Map<String, DerivedColumn> simpleChecks,
      Optional<PrimaryKey> primaryKey) {
    this(tableName, columns, simpleCheckDefinitions, simpleChecks, primaryKey, Maps.newHashMap());
  }

  @Override
  public ImmutableMap<String, BasicColumn> columns() {
    return ImmutableMap.copyOf(columns);
  }

  @Override
  public SeekableTableReader reader(Resources resources) throws IOException {
    return resources.reader(new Ident(tableName));
  }

  @Override
  public int indexOf(Ident ident) {
    if (ident.isSimple()) {
      return indexOf(columns().keySet(), ident);
    } else {
      if (ident.head().equals(tableName)) {
        return indexOf(columns().keySet(), ident.tail());
      } else {
        return -1;
      }
    }
  }

  @Override
  public ColumnMeta column(Ident ident) throws ModelException {
    if (ident.isSimple()) {
      return column(ident, columns);
    } else {
      if (ident.head().equals(tableName)) {
        return column(ident.tail(), columns);
      } else {
        throw new ModelException("Table mismatch '" + ident.head() + "' instead of '" + tableName + "'.");
      }
    }
  }

  @Override
  public MetaResources readResources() {
    return MetaResources.readTable(this);
  }

  public String tableName() {
    return tableName;
  }

  public List<SimpleCheckDefinition> simpleCheckDefinitions() {
    return Collections.unmodifiableList(simpleCheckDefinitions);
  }

  public Map<String, DerivedColumn> simpleChecks() {
    return Collections.unmodifiableMap(simpleChecks);
  }

  public Optional<PrimaryKey> primaryKey() {
    return primaryKey;
  }

  public Map<String, ForeignKey> foreignKeys() {
    return Collections.unmodifiableMap(foreignKeys);
  }

  public Map<String, ForeignKey> reverseForeignKeys() {
    return Collections.unmodifiableMap(reverseForeignKeys);
  }

  public static MaterializedTableMeta simpleTable(
      String name, LinkedHashMap<String, BasicColumn> columns) {
    return new MaterializedTableMeta(
        name,
        columns,
        Lists.newArrayList(),
        Maps.newHashMap(),
        Optional.empty());
  }

  public void addForeignKey(ForeignKey foreignKey) {
    foreignKeys.put(foreignKey.getName(), foreignKey);
  }

  public void addReverseForeignKey(ForeignKey foreignKey) {
    reverseForeignKeys.put(foreignKey.getName(), foreignKey);
  }
}
