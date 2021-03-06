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
package com.cosyan.db.lang.expr;

import java.io.IOException;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.cosyan.db.lang.sql.Tokens;
import com.cosyan.db.lang.sql.Tokens.Loc;
import com.cosyan.db.meta.Dependencies.TableDependencies;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.AggrTables;
import com.cosyan.db.model.AggrTables.NotAggrTableException;
import com.cosyan.db.model.BuiltinFunctions;
import com.cosyan.db.model.BuiltinFunctions.SimpleFunction;
import com.cosyan.db.model.BuiltinFunctions.TypedAggrFunction;
import com.cosyan.db.model.ColumnMeta;
import com.cosyan.db.model.ColumnMeta.AggrColumn;
import com.cosyan.db.model.ColumnMeta.DerivedColumnWithDeps;
import com.cosyan.db.model.CompiledObject;
import com.cosyan.db.model.CompiledObject.ColumnList;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.DataTypes.DataType;
import com.cosyan.db.model.DerivedTables.KeyValueTableMeta;
import com.cosyan.db.model.Ident;
import com.cosyan.db.model.TableContext;
import com.cosyan.db.model.TableMeta;
import com.cosyan.db.transaction.MetaResources;
import com.cosyan.db.transaction.Resources;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FuncCallExpression extends Expression {
  private final Ident ident;
  @Nullable
  private final Expression object;
  private final ImmutableList<Expression> args;

  public FuncCallExpression(Ident ident, @Nullable Expression object, ImmutableList<Expression> args) {
    this.ident = ident;
    this.object = object;
    this.args = args;
  }

  public FuncCallExpression(Ident ident) {
    this.ident = ident;
    this.object = null;
    this.args = ImmutableList.of();
  }

  public static FuncCallExpression of(Ident ident) {
    return new FuncCallExpression(ident);
  }

  private boolean isAggr() {
    return object == null && args.size() > 0 && BuiltinFunctions.AGGREGATION_NAMES.contains(ident.getString());
  }

  private DerivedColumnWithDeps simpleFunction(TableMeta sourceTable, @Nullable ColumnMeta objColumn)
      throws ModelException {
    SimpleFunction<?> function = BuiltinFunctions.simpleFunction(ident);
    ImmutableList.Builder<ColumnMeta> argColumnsBuilder = ImmutableList.builder();
    if (objColumn != null) {
      argColumnsBuilder.add(objColumn);
    }
    for (int i = 0; i < args.size(); i++) {
      argColumnsBuilder.add(args.get(i).compileColumn(sourceTable));
    }

    ImmutableList<ColumnMeta> argColumns = argColumnsBuilder.build();
    if (function.getArgTypes().size() != argColumns.size()) {
      throw new ModelException(String.format("Expected %s columns for function '%s' but got %s.",
          function.getArgTypes().size(), ident.getString(), argColumns.size()), ident);
    }
    MetaResources resources = MetaResources.empty();
    TableDependencies tableDependencies = new TableDependencies();
    for (int i = 0; i < function.getArgTypes().size(); i++) {
      DataType<?> expectedType = function.argType(i);
      DataType<?> dataType = argColumns.get(i).getType();
      Expression expr = objColumn != null ? i == 0 ? object : args.get(i - 1) : args.get(i);
      if (!(expectedType == DataTypes.DoubleType && dataType == DataTypes.LongType)) {
        // Skip check for Double/Long pairs, there will be an implicit type conversion.
        assertType(expectedType, dataType, expr.loc());
      }
      resources = resources.merge(argColumns.get(i).readResources());
      tableDependencies.addToThis(argColumns.get(i).tableDependencies());
    }
    return new DerivedColumnWithDeps(function.getReturnType(), tableDependencies, resources) {

      @Override
      public Object value(Object[] values, Resources resources, TableContext context) throws IOException {
        ImmutableList.Builder<Object> paramsBuilder = ImmutableList.builder();
        for (int i = 0; i < function.getArgTypes().size(); i++) {
          Object value = argColumns.get(i).value(values, resources, context);
          if (value == null) {
            return null;
          }
          if (function.argType(i) == DataTypes.DoubleType && value instanceof Long) {
            // Implicit type conversion from Long to Double.
            value = Double.valueOf((Long) value);
          }
          paramsBuilder.add(value);
        }
        ImmutableList<Object> params = paramsBuilder.build();
        return function.call(params);
      }

      @Override
      public String print(Object[] values, Resources resources, TableContext context) throws IOException {
        StringJoiner sj = new StringJoiner(", ");
        for (ColumnMeta column : argColumns) {
          sj.add(column.print(values, resources, context));
        }
        return function.getName() + "(" + sj.toString() + ")";
      }
    };
  }

  private AggrColumn aggrFunction(TableMeta sourceTable, Expression arg)
      throws ModelException {
    if (!(sourceTable instanceof AggrTables)) {
      throw new NotAggrTableException(ident);
    }
    AggrTables aggrTable = (AggrTables) sourceTable;
    KeyValueTableMeta keyValueTableMeta = aggrTable.sourceTable();
    int shift = keyValueTableMeta.getKeyColumns().size();
    ColumnMeta argColumn = arg.compileColumn(keyValueTableMeta.getSourceTable());
    final TypedAggrFunction<?> function = BuiltinFunctions.aggrFunction(ident, argColumn.getType());
    AggrColumn aggrColumn = new AggrColumn(
        aggrTable,
        function.getReturnType(),
        argColumn,
        shift + aggrTable.numAggrColumns(),
        function);
    aggrTable.addAggrColumn(aggrColumn);
    return aggrColumn;
  }

  @Override
  public CompiledObject compile(TableMeta sourceTable)
      throws ModelException {
    if (object == null) {
      if (args.isEmpty()) {
        if (sourceTable.hasTable(ident)) {
          return sourceTable.table(ident);
        } else {
          return sourceTable.column(ident);
        }
      } else {
        if (isAggr()) {
          if (args.size() != 1) {
            throw new ModelException("Invalid number of arguments for aggregator: " + args.size() + ".", ident);
          }
          return aggrFunction(sourceTable, Iterables.getOnlyElement(args));
        } else {
          return simpleFunction(sourceTable, null);
        }
      }
    } else {
      if (isAggr()) {
        if (args.size() > 0) {
          throw new ModelException("Invalid number of arguments for aggregator: " + args.size() + ".", ident);
        }
        return aggrFunction(sourceTable, object);
      } else { // Not aggregator.
        CompiledObject obj = object.compile(sourceTable);
        if (obj instanceof TableMeta) {
          TableMeta tableMeta = (TableMeta) obj;
          if (args.isEmpty()) {
            if (tableMeta.hasTable(ident)) {
              return tableMeta.table(ident);
            } else if (ident.is(Tokens.ASTERISK)) {
              ImmutableMap.Builder<String, ColumnMeta> columns = ImmutableMap.builder();
              for (String name : tableMeta.columnNames()) {
                columns.put(name, tableMeta.column(new Ident(name)));
              }
              return new ColumnList(columns.build());
            } else {
              return tableMeta.column(ident);
            }
          }
        } else if (obj instanceof ColumnMeta) {
          ColumnMeta columnMeta = (ColumnMeta) obj;
          return simpleFunction(sourceTable, columnMeta);
        }
      }
    }
    throw new ModelException(String.format("Invalid identifier '%s'.", ident.getString()), ident);
  }

  @Override
  public String getName(String def) {
    if (args.size() == 0 && !isAggr()) {
      return ident.getString();
    } else {
      return super.getName(def);
    }
  }

  @Override
  public String print() {
    String argsStr = args.isEmpty() ? ""
        : "(" + args.stream().map(arg -> arg.print()).collect(Collectors.joining(", ")) + ")";
    if (object == null) {
      return ident.getString() + argsStr;
    } else {
      return object.print() + "." + ident.getString() + argsStr;
    }
  }

  @Override
  public Loc loc() {
    return ident.getLoc();
  }
}
