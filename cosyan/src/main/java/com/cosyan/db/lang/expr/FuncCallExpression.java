package com.cosyan.db.lang.expr;

import java.io.IOException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.cosyan.db.lang.sql.Parser.ParserException;
import com.cosyan.db.lang.sql.SyntaxTree;
import com.cosyan.db.lang.sql.SyntaxTree.AggregationExpression;
import com.cosyan.db.meta.MetaRepo.ModelException;
import com.cosyan.db.model.BuiltinFunctions;
import com.cosyan.db.model.BuiltinFunctions.SimpleFunction;
import com.cosyan.db.model.BuiltinFunctions.TypedAggrFunction;
import com.cosyan.db.model.ColumnMeta;
import com.cosyan.db.model.ColumnMeta.AggrColumn;
import com.cosyan.db.model.ColumnMeta.DerivedColumnWithDeps;
import com.cosyan.db.model.CompiledObject;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.Dependencies.TableDependencies;
import com.cosyan.db.model.DerivedTables.KeyValueTableMeta;
import com.cosyan.db.model.Ident;
import com.cosyan.db.model.SourceValues;
import com.cosyan.db.model.TableMeta;
import com.google.common.collect.ImmutableList;
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
  private final AggregationExpression aggregation;

  public FuncCallExpression(Ident ident, @Nullable Expression object, ImmutableList<Expression> args)
      throws ParserException {
    this.ident = ident;
    this.object = object;
    this.args = args;
    if (isAggr()) {
      aggregation = AggregationExpression.YES;
    } else {
      long yes = args.stream().filter(arg -> arg.isAggregation() == AggregationExpression.YES).count();
      long no = args.stream().filter(arg -> arg.isAggregation() == AggregationExpression.NO).count();
      if (no == 0 && yes == 0) {
        aggregation = AggregationExpression.EITHER;
      } else if (no == 0) {
        aggregation = AggregationExpression.YES;
      } else if (yes == 0) {
        aggregation = AggregationExpression.NO;
      } else {
        throw new ParserException("Inconsistent parameter.");
      }
    }
  }

  public FuncCallExpression(Ident ident) {
    this.ident = ident;
    this.object = null;
    this.args = ImmutableList.of();
    if (isAggr()) {
      aggregation = AggregationExpression.YES;
    } else {
      aggregation = AggregationExpression.EITHER;
    }
  }

  public static FuncCallExpression of(Ident ident) {
    return new FuncCallExpression(ident);
  }

  private boolean isAggr() {
    return BuiltinFunctions.AGGREGATION_NAMES.contains(ident.getString());
  }

  private DerivedColumnWithDeps simpleFunction(TableMeta sourceTable, @Nullable ColumnMeta objColumn)
      throws ModelException {
    SimpleFunction<?> function = BuiltinFunctions.simpleFunction(ident.getString());
    ImmutableList.Builder<ColumnMeta> argColumnsBuilder = ImmutableList.builder();
    if (objColumn != null) {
      argColumnsBuilder.add(objColumn);
    }
    TableDependencies tableDependencies = new TableDependencies();
    for (int i = 0; i < args.size(); i++) {
      ColumnMeta col = args.get(i).compileColumn(sourceTable);
      argColumnsBuilder.add(col);
      tableDependencies.add(col.tableDependencies());
    }
    ImmutableList<ColumnMeta> argColumns = argColumnsBuilder.build();
    for (int i = 0; i < function.getArgTypes().size(); i++) {
      SyntaxTree.assertType(function.getArgTypes().get(i), argColumns.get(i).getType());
    }
    return new DerivedColumnWithDeps(function.getReturnType(), tableDependencies) {

      @Override
      public Object getValue(SourceValues values) throws IOException {
        ImmutableList.Builder<Object> paramsBuilder = ImmutableList.builder();
        for (ColumnMeta column : argColumns) {
          paramsBuilder.add(column.getValue(values));
        }
        ImmutableList<Object> params = paramsBuilder.build();
        for (Object param : params) {
          if (param == DataTypes.NULL) {
            return DataTypes.NULL;
          }
        }
        return function.call(params);
      }
    };
  }

  private AggrColumn aggrFunction(TableMeta sourceTable, Expression arg, ExtraInfoCollector collector)
      throws ModelException {
    if (!(sourceTable instanceof KeyValueTableMeta)) {
      throw new ModelException("Aggregators are not allowed here.");
    }
    KeyValueTableMeta outerTable = (KeyValueTableMeta) sourceTable;
    ColumnMeta argColumn = arg.compileColumn(outerTable.getSourceTable());
    final TypedAggrFunction<?> function = BuiltinFunctions.aggrFunction(ident.getString(), argColumn.getType());

    AggrColumn aggrColumn = new AggrColumn(
        function.getReturnType(),
        argColumn,
        outerTable.getKeyColumns().size() + collector.numAggrColumns(),
        function);
    collector.addAggrColumn(aggrColumn);
    return aggrColumn;
  }

  @Override
  public CompiledObject compile(TableMeta sourceTable, ExtraInfoCollector collector)
      throws ModelException {
    if (object == null) {
      if (args.isEmpty()) {
        if (sourceTable.hasTable(ident)) {
          return sourceTable.table(ident);
        } else {
          return sourceTable.column(ident).toMeta();
        }
      } else {
        if (isAggr()) {
          if (args.size() != 1) {
            throw new ModelException("Invalid number of arguments for aggregator: " + args.size() + ".");
          }
          return aggrFunction(sourceTable, Iterables.getOnlyElement(args), collector);
        } else {
          return simpleFunction(sourceTable, null);
        }
      }
    } else {
      if (isAggr()) {
        if (args.size() > 0) {
          throw new ModelException("Invalid number of arguments for aggregator: " + args.size() + ".");
        }
        return aggrFunction(sourceTable, object, collector);
      } else {
        CompiledObject obj = object.compile(sourceTable);
        if (obj instanceof TableMeta) {
          TableMeta tableMeta = (TableMeta) obj;
          if (args.isEmpty()) {
            if (tableMeta.hasTable(ident)) {
              return tableMeta.table(ident);
            } else {
              return tableMeta.column(ident).toMeta();
            }
          } else {
            throw new ModelException(String.format("Arguments are not allowed here.", ident.getString()));
          }
        } else if (obj instanceof ColumnMeta) {
          ColumnMeta columnMeta = (ColumnMeta) obj;
          return simpleFunction(sourceTable, columnMeta);
        }
      }
    }
    throw new ModelException(String.format("Invalid identifier '%s'.", ident.getString()));
  }

  @Override
  public AggregationExpression isAggregation() {
    return aggregation;
  }

  @Override
  public String getName(String def) {
    if (args.size() == 0) {
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
}
