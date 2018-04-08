package com.cosyan.db.transaction;

import java.util.HashMap;
import java.util.Map;

import com.cosyan.db.model.BasicColumn;
import com.cosyan.db.model.Keys.ForeignKey;
import com.cosyan.db.model.Keys.ReverseForeignKey;
import com.cosyan.db.model.MaterializedTableMeta;
import com.cosyan.db.util.Util;
import com.google.common.collect.ImmutableMap;

import lombok.Data;

public class MetaResources {

  @Data
  public static class Resource {
    private final String resourceId;
    private final boolean write;

    public Resource merge(Resource other) {
      assert (this.resourceId.equals(other.resourceId));
      return new Resource(
          this.resourceId,
          this.write || other.write);
    }
  }

  @Data
  public static class TableMetaResource {
    private final MaterializedTableMeta tableMeta;
    private final boolean select;
    private final boolean insert;
    private final boolean delete;
    private final boolean update;
    private final boolean foreignIndexes;
    private final boolean reverseForeignIndexes;

    public TableMetaResource merge(TableMetaResource other) {
      assert (this.tableMeta == other.tableMeta);
      return new TableMetaResource(
          this.tableMeta,
          this.select || other.select,
          this.insert || other.insert,
          this.delete || other.delete,
          this.update || other.update,
          this.foreignIndexes || other.foreignIndexes,
          this.reverseForeignIndexes || other.reverseForeignIndexes);
    }

    public boolean write() {
      return insert || delete || update;
    }

    public ImmutableMap<String, Resource> resources() {
      Map<String, Resource> builder = new HashMap<>();
      String tableName = tableMeta.tableName();
      builder.put(tableName, new Resource(tableName, write()));
      for (BasicColumn column : tableMeta.columns().values()) {
        if (column.isIndexed()) {
          String indexName = tableName + "." + column.getName();
          builder.put(indexName, new Resource(indexName, write()));
        }
      }
      if (foreignIndexes) {
        for (ForeignKey foreignKey : tableMeta.foreignKeys().values()) {
          String indexName = foreignKey.getRefTable().tableName() + "." + foreignKey.getRefColumn().getName();
          builder.put(indexName, new Resource(indexName, /* write= */false));
        }
      }
      if (reverseForeignIndexes) {
        for (ReverseForeignKey foreignKey : tableMeta.reverseForeignKeys().values()) {
          String indexName = foreignKey.getRefTable().tableName() + "." + foreignKey.getRefColumn().getName();
          builder.put(indexName, new Resource(indexName, /* write= */false));
        }
      }
      return ImmutableMap.copyOf(builder);
    }
  }

  private final ImmutableMap<String, TableMetaResource> tables;

  public MetaResources(ImmutableMap<String, TableMetaResource> tables) {
    this.tables = tables;
  }

  public MetaResources merge(MetaResources other) {
    return new MetaResources(
        Util.merge(this.tables, other.tables, TableMetaResource::merge));
  }

  public Iterable<TableMetaResource> tables() {
    return tables.values();
  }

  public static MetaResources readTable(MaterializedTableMeta tableMeta) {
    return new MetaResources(ImmutableMap.of(
        tableMeta.tableName(),
        new TableMetaResource(
            tableMeta,
            /* select= */true,
            /* insert= */false,
            /* delete= */false,
            /* update= */false,
            /* foreignIndexes= */false,
            /* reverseForeignIndexes= */false)));
  }

  public static MetaResources updateTable(MaterializedTableMeta tableMeta) {
    return new MetaResources(ImmutableMap.of(
        tableMeta.tableName(),
        new TableMetaResource(
            tableMeta,
            /* select= */false,
            /* insert= */false,
            /* delete= */false,
            /* update= */true,
            /* foreignIndexes= */true,
            /* reverseForeignIndexes= */true)));
  }

  public static MetaResources insertIntoTable(MaterializedTableMeta tableMeta) {
    return new MetaResources(ImmutableMap.of(
        tableMeta.tableName(),
        new TableMetaResource(
            tableMeta,
            /* select= */false,
            /* insert= */true,
            /* delete= */false,
            /* update= */false,
            /* foreignIndexes= */true, // New records have to satisfy foreign key constraints.
            /* reverseForeignIndexes= */false)));
  }

  public static MetaResources deleteFromTable(MaterializedTableMeta tableMeta) {
    return new MetaResources(ImmutableMap.of(
        tableMeta.tableName(),
        new TableMetaResource(
            tableMeta,
            /* select= */false,
            /* insert= */false,
            /* delete= */true,
            /* update= */false,
            /* foreignIndexes= */false,
            /* reverseForeignIndexes= */true))); // Cannot delete records referenced by foreign keys.
  }

  public static MetaResources empty() {
    return new MetaResources(ImmutableMap.of());
  }

  public Iterable<Resource> all() {
    return Util.merge(
        Util.mapValues(tables, TableMetaResource::resources).values(),
        Resource::merge).values();
  }
}
