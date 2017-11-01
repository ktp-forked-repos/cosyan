package com.cosyan.db.lang.sql;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cosyan.db.lang.expr.BinaryExpression;
import com.cosyan.db.lang.expr.Expression;
import com.cosyan.db.lang.expr.Expression.IdentExpression;
import com.cosyan.db.lang.expr.Expression.UnaryExpression;
import com.cosyan.db.lang.expr.FuncCallExpression;
import com.cosyan.db.lang.expr.Literals.DoubleLiteral;
import com.cosyan.db.lang.expr.Literals.Literal;
import com.cosyan.db.lang.expr.Literals.LongLiteral;
import com.cosyan.db.lang.expr.Literals.StringLiteral;
import com.cosyan.db.lang.sql.AlterStatement.AlterTableAddColumn;
import com.cosyan.db.lang.sql.AlterStatement.AlterTableAlterColumn;
import com.cosyan.db.lang.sql.AlterStatement.AlterTableDropColumn;
import com.cosyan.db.lang.sql.CreateStatement.ColumnDefinition;
import com.cosyan.db.lang.sql.CreateStatement.ConstraintDefinition;
import com.cosyan.db.lang.sql.CreateStatement.CreateIndex;
import com.cosyan.db.lang.sql.CreateStatement.CreateTable;
import com.cosyan.db.lang.sql.CreateStatement.ForeignKeyDefinition;
import com.cosyan.db.lang.sql.CreateStatement.PrimaryKeyDefinition;
import com.cosyan.db.lang.sql.CreateStatement.RuleDefinition;
import com.cosyan.db.lang.sql.DeleteStatement.Delete;
import com.cosyan.db.lang.sql.DropStatement.DropIndex;
import com.cosyan.db.lang.sql.DropStatement.DropTable;
import com.cosyan.db.lang.sql.InsertIntoStatement.InsertInto;
import com.cosyan.db.lang.sql.SelectStatement.AsExpression;
import com.cosyan.db.lang.sql.SelectStatement.AsTable;
import com.cosyan.db.lang.sql.SelectStatement.AsteriskExpression;
import com.cosyan.db.lang.sql.SelectStatement.JoinExpr;
import com.cosyan.db.lang.sql.SelectStatement.Select;
import com.cosyan.db.lang.sql.SelectStatement.Table;
import com.cosyan.db.lang.sql.SelectStatement.TableExpr;
import com.cosyan.db.lang.sql.SelectStatement.TableRef;
import com.cosyan.db.lang.sql.SyntaxTree.MetaStatement;
import com.cosyan.db.lang.sql.SyntaxTree.Statement;
import com.cosyan.db.lang.sql.Tokens.Token;
import com.cosyan.db.lang.sql.UpdateStatement.SetExpression;
import com.cosyan.db.lang.sql.UpdateStatement.Update;
import com.cosyan.db.model.DataTypes;
import com.cosyan.db.model.DataTypes.DataType;
import com.cosyan.db.model.Ident;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.PeekingIterator;

public class Parser {

  public boolean isMeta(PeekingIterator<Token> tokens) {
    if (tokens.peek().is(Tokens.CREATE) ||
        tokens.peek().is(Tokens.ALTER) ||
        tokens.peek().is(Tokens.DROP)) {
      return true;
    }
    return false;
  }

  public ImmutableList<Statement> parseStatements(PeekingIterator<Token> tokens) throws ParserException {
    ImmutableList.Builder<Statement> roots = ImmutableList.builder();
    while (tokens.hasNext()) {
      Statement root = parseStatement(tokens);
      roots.add(root);
      assertNext(tokens, String.valueOf(Tokens.COMMA_COLON));
    }
    return roots.build();
  }

  public MetaStatement parseMetaStatement(PeekingIterator<Token> tokens) throws ParserException {
    if (tokens.peek().is(Tokens.CREATE)) {
      return parseCreate(tokens);
    } else if (tokens.peek().is(Tokens.DROP)) {
      return parseDrop(tokens);
    } else if (tokens.peek().is(Tokens.ALTER)) {
      return parseAlter(tokens);
    }
    throw new ParserException("Syntax error, expected create, drop or alter.");
  }

  private Statement parseStatement(PeekingIterator<Token> tokens) throws ParserException {
    if (tokens.peek().is(Tokens.SELECT)) {
      return parseSelect(tokens);
    } else if (tokens.peek().is(Tokens.INSERT)) {
      return parseInsert(tokens);
    } else if (tokens.peek().is(Tokens.DELETE)) {
      return parseDelete(tokens);
    } else if (tokens.peek().is(Tokens.UPDATE)) {
      return parseUpdate(tokens);
    }
    throw new ParserException("Syntax error, expected select, insert, delete or update.");
  }

  private Delete parseDelete(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.DELETE);
    assertNext(tokens, Tokens.FROM);
    Ident ident = parseSimpleIdent(tokens);
    assertNext(tokens, Tokens.WHERE);
    Expression where = parseExpression(tokens, 0);
    return new Delete(ident, where);
  }

  private InsertInto parseInsert(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.INSERT);
    assertNext(tokens, Tokens.INTO);
    Ident ident = parseSimpleIdent(tokens);
    Optional<ImmutableList<Ident>> columns;
    if (tokens.peek().is(Tokens.PARENT_OPEN)) {
      tokens.next();
      ImmutableList.Builder<Ident> builder = ImmutableList.builder();
      while (true) {
        builder.add(parseSimpleIdent(tokens));
        if (tokens.peek().is(Tokens.COMMA)) {
          tokens.next();
        } else {
          assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
          break;
        }
      }
      columns = Optional.of(builder.build());
    } else {
      columns = Optional.empty();
    }
    assertNext(tokens, Tokens.VALUES);

    ImmutableList.Builder<ImmutableList<Literal>> valuess = ImmutableList.builder();
    while (true) {
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      ImmutableList.Builder<Literal> values = ImmutableList.builder();
      while (true) {
        values.add(parseLiteral(tokens));
        if (tokens.peek().is(Tokens.COMMA)) {
          tokens.next();
        } else {
          assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
          break;
        }
      }
      valuess.add(values.build());
      if (tokens.peek().is(Tokens.COMMA)) {
        tokens.next();
      } else {
        break;
      }
    }
    return new InsertInto(ident, columns, valuess.build());
  }

  private Literal parseLiteral(PeekingIterator<Token> tokens) throws ParserException {
    Expression expr = parsePrimary(tokens);
    if (!(expr instanceof Literal)) {
      throw new ParserException("Expected literal but got '" + expr + "'.");
    }
    return (Literal) expr;
  }

  private Update parseUpdate(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.UPDATE);
    Ident ident = parseSimpleIdent(tokens);
    assertNext(tokens, Tokens.SET);
    ImmutableList.Builder<SetExpression> updates = ImmutableList.builder();
    while (true) {
      Ident columnIdent = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.EQ));
      Expression expr = parseExpression(tokens, 0);
      updates.add(new SetExpression(columnIdent, expr));
      if (tokens.peek().is(Tokens.COMMA)) {
        tokens.next();
      } else {
        break;
      }
    }

    Optional<Expression> where;
    if (tokens.peek().is(Tokens.WHERE)) {
      tokens.next();
      where = Optional.of(parseExpression(tokens, 0));
    } else {
      where = Optional.empty();
    }
    return new Update(ident, updates.build(), where);
  }

  private MetaStatement parseCreate(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.CREATE);
    assertPeek(tokens, Tokens.TABLE, Tokens.INDEX);
    if (tokens.peek().is(Tokens.TABLE)) {
      tokens.next();
      Ident ident = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      ImmutableList.Builder<ColumnDefinition> columns = ImmutableList.builder();
      ImmutableList.Builder<ConstraintDefinition> constraints = ImmutableList.builder();
      while (true) {
        if (tokens.peek().is(Tokens.CONSTRAINT)) {
          constraints.add(parseConstraint(tokens));
        } else {
          columns.add(parseColumnDefinition(tokens));
        }
        if (tokens.peek().is(Tokens.COMMA)) {
          tokens.next();
        } else {
          assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
          break;
        }
      }
      return new CreateTable(ident.getString(), columns.build(), constraints.build());
    } else {
      assertNext(tokens, Tokens.INDEX);
      Ident ident = parseIdent(tokens);
      return new CreateIndex(ident);
    }
  }

  private MetaStatement parseDrop(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.DROP);
    assertPeek(tokens, Tokens.TABLE, Tokens.INDEX);
    if (tokens.peek().is(Tokens.TABLE)) {
      tokens.next();
      Ident ident = parseSimpleIdent(tokens);
      return new DropTable(ident);
    } else {
      assertNext(tokens, Tokens.INDEX);
      Ident ident = parseIdent(tokens);
      return new DropIndex(ident);
    }
  }

  private MetaStatement parseAlter(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.ALTER);
    assertNext(tokens, Tokens.TABLE);
    Ident ident = parseSimpleIdent(tokens);
    if (tokens.peek().is(Tokens.ADD)) {
      tokens.next();
      ColumnDefinition column = parseColumnDefinition(tokens);
      return new AlterTableAddColumn(ident, column);
    } else if (tokens.peek().is(Tokens.DROP)) {
      tokens.next();
      Ident columnName = parseSimpleIdent(tokens);
      return new AlterTableDropColumn(ident, columnName);
    } else if (tokens.peek().is(Tokens.ALTER) || tokens.peek().is(Tokens.MODIFY)) {
      tokens.next();
      ColumnDefinition column = parseColumnDefinition(tokens);
      return new AlterTableAlterColumn(ident, column);
    } else {
      throw new ParserException("Unsupported alter operation '" + tokens.peek() + "'.");
    }
  }

  private ConstraintDefinition parseConstraint(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.CONSTRAINT);
    Ident ident = parseSimpleIdent(tokens);
    if (tokens.peek().is(Tokens.CHECK)) {
      tokens.next();
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      Expression expr = parseExpression(tokens, 0);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      return new RuleDefinition(ident.getString(), expr);
    } else if (tokens.peek().is(Tokens.PRIMARY)) {
      tokens.next();
      assertNext(tokens, Tokens.KEY);
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      Ident column = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      return new PrimaryKeyDefinition(ident.getString(), column);
    } else if (tokens.peek().is(Tokens.FOREIGN)) {
      tokens.next();
      assertNext(tokens, Tokens.KEY);
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      Ident column = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      assertNext(tokens, Tokens.REFERENCES);
      Ident refTable = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_OPEN));
      Ident refColumn = parseSimpleIdent(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      return new ForeignKeyDefinition(ident.getString(), column, refTable, refColumn);
    } else {
      throw new ParserException("Unsupported constraint '" + tokens.peek() + "'.");
    }
  }

  private ColumnDefinition parseColumnDefinition(PeekingIterator<Token> tokens) throws ParserException {
    Ident ident = parseSimpleIdent(tokens);
    DataType<?> type = parseDataType(tokens);
    boolean unique;
    if (tokens.peek().is(Tokens.UNIQUE)) {
      tokens.next();
      unique = true;
    } else {
      unique = false;
    }
    boolean nullable;
    if (tokens.peek().is(Tokens.NOT)) {
      tokens.next();
      assertNext(tokens, Tokens.NULL);
      nullable = false;
    } else {
      nullable = true;
    }
    return new ColumnDefinition(ident.getString(), type, nullable, unique);
  }

  private DataType<?> parseDataType(PeekingIterator<Token> tokens) throws ParserException {
    Token token = tokens.next();
    if (token.is(Tokens.VARCHAR)) {
      return DataTypes.StringType;
    } else if (token.is(Tokens.INTEGER)) {
      return DataTypes.LongType;
    } else if (token.is(Tokens.FLOAT)) {
      return DataTypes.DoubleType;
    } else if (token.is(Tokens.TIMESTAMP)) {
      return DataTypes.DateType;
    } else if (token.is(Tokens.BOOLEAN)) {
      return DataTypes.BoolType;
    }
    throw new ParserException("Unknown data type '" + token + "'.");
  }

  private Select parseSelect(PeekingIterator<Token> tokens) throws ParserException {
    assertNext(tokens, Tokens.SELECT);
    boolean distinct = false;
    if (tokens.peek().is(Tokens.DISTINCT)) {
      tokens.next();
      distinct = true;
    }
    ImmutableList<Expression> columns = parseExprs(tokens, true, Tokens.FROM);
    tokens.next();
    Table table = parseTable(tokens);
    if (tokens.peek().is(Tokens.INNER) || tokens.peek().is(Tokens.LEFT) || tokens.peek().is(Tokens.RIGHT)) {
      Token joinType = tokens.next();
      assertNext(tokens, Tokens.JOIN);
      Table rightTable = parseTable(tokens);
      assertNext(tokens, Tokens.ON);
      Expression onExpr = parseExpression(tokens, 0);
      table = new JoinExpr(joinType, table, rightTable, onExpr);
    }
    Optional<Expression> where;
    if (tokens.peek().is(Tokens.WHERE)) {
      tokens.next();
      where = Optional.of(parseExpression(tokens, 0));
    } else {
      where = Optional.empty();
    }
    Optional<ImmutableList<Expression>> groupBy;
    Optional<Expression> having;
    if (tokens.peek().is(Tokens.GROUP)) {
      tokens.next();
      assertNext(tokens, Tokens.BY);
      groupBy = Optional.of(parseExprs(tokens, true,
          String.valueOf(Tokens.COMMA_COLON), String.valueOf(Tokens.PARENT_CLOSED), Tokens.HAVING, Tokens.ORDER));
      if (tokens.peek().is(Tokens.HAVING)) {
        tokens.next();
        having = Optional.of(parseExpression(tokens, 0));
      } else {
        having = Optional.empty();
      }
    } else {
      groupBy = Optional.empty();
      having = Optional.empty();
    }
    Optional<ImmutableList<Expression>> orderBy;
    if (tokens.peek().is(Tokens.ORDER)) {
      tokens.next();
      assertNext(tokens, Tokens.BY);
      orderBy = Optional.of(parseExprs(tokens, true,
          String.valueOf(Tokens.COMMA_COLON), String.valueOf(Tokens.PARENT_CLOSED)));
    } else {
      orderBy = Optional.empty();
    }
    assertPeek(tokens, String.valueOf(Tokens.COMMA_COLON), String.valueOf(Tokens.PARENT_CLOSED));
    return new Select(columns, table, where, groupBy, having, orderBy, distinct);
  }

  private Expression parsePrimary(PeekingIterator<Token> tokens) throws ParserException {
    Token token = tokens.peek();
    if (token.is(Tokens.PARENT_OPEN)) {
      tokens.next();
      Expression expr = parseExpression(tokens, 0);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      return expr;
    } else if (token.is(Tokens.ASTERISK)) {
      tokens.next();
      return new AsteriskExpression();
    } else if (token.isIdent()) {
      Ident ident = new Ident(tokens.next().getString());
      if (tokens.peek().is(Tokens.PARENT_OPEN)) {
        tokens.next();
        if (ident.is(Tokens.COUNT) && tokens.peek().is(Tokens.DISTINCT)) {
          tokens.next();
          ImmutableList<Expression> argExprs = parseExprs(tokens, false, String.valueOf(Tokens.PARENT_CLOSED));
          tokens.next();
          return new FuncCallExpression(new Ident("count$distinct"), argExprs);
        } else {
          ImmutableList<Expression> argExprs = parseExprs(tokens, false, String.valueOf(Tokens.PARENT_CLOSED));
          tokens.next();
          return new FuncCallExpression(ident, argExprs);
        }
      } else {
        return new IdentExpression(ident);
      }
    } else if (token.isInt()) {
      tokens.next();
      return new LongLiteral(Long.valueOf(token.getString()));
    } else if (token.isFloat()) {
      tokens.next();
      return new DoubleLiteral(Double.valueOf(token.getString()));
    } else if (token.isString()) {
      tokens.next();
      return new StringLiteral(token.getString());
    } else {
      throw new ParserException("Expected literal but got " + token.getString() + ".");
    }
  }

  private ImmutableList<Expression> parseExprs(
      PeekingIterator<Token> tokens,
      boolean allowAlias,
      String... terminators) throws ParserException {
    ImmutableList.Builder<Expression> exprs = ImmutableList.builder();
    while (true) {
      if (ImmutableSet.copyOf(terminators).contains(tokens.peek().getString())) {
        break;
      }
      Expression expr = parseExpression(tokens, 0);
      if (allowAlias && tokens.peek().is(Tokens.AS)) {
        tokens.next();
        Ident ident = parseIdent(tokens);
        exprs.add(new AsExpression(ident, expr));
      } else {
        exprs.add(expr);
      }
      if (tokens.peek().is(Tokens.COMMA)) {
        tokens.next();
      } else {
        assertPeek(tokens, terminators);
        break;
      }
    }
    return exprs.build();
  }

  private Ident parseIdent(PeekingIterator<Token> tokens) throws ParserException {
    Token token = tokens.next();
    if (token.isIdent()) {
      return new Ident(token.getString());
    } else {
      throw new ParserException("Expected identifier but got " + token.getString() + ".");
    }
  }

  private Ident parseSimpleIdent(PeekingIterator<Token> tokens) throws ParserException {
    Ident ident = parseIdent(tokens);
    if (!ident.isSimple()) {
      throw new ParserException("Expected simple identifier but got '" + ident + "'.");
    }
    return ident;
  }

  private Table parseTable(PeekingIterator<Token> tokens) throws ParserException {
    final Table table;
    if (tokens.peek().is(Tokens.PARENT_OPEN)) {
      tokens.next();
      Select select = parseSelect(tokens);
      assertNext(tokens, String.valueOf(Tokens.PARENT_CLOSED));
      table = new TableExpr(select);
    } else {
      table = new TableRef(parseIdent(tokens));
    }
    if (tokens.peek().is(Tokens.AS)) {
      tokens.next();
      return new AsTable(parseIdent(tokens), table);
    } else {
      return table;
    }
  }

  public Expression parseExpression(PeekingIterator<Token> tokens) throws ParserException {
    return parseExpression(tokens, 0);
  }

  private Expression parseExpression(PeekingIterator<Token> tokens, int precedence) throws ParserException {
    if (precedence >= Tokens.BINARY_OPERATORS_PRECEDENCE.size()) {
      return parsePrimary(tokens);
    } else if (tokens.peek().is(Tokens.NOT)
        && Tokens.BINARY_OPERATORS_PRECEDENCE.get(precedence).contains(Tokens.NOT)) {
      return new UnaryExpression(tokens.next(), parseExpression(tokens, precedence + 1));
    } else {
      Expression primary = parseExpression(tokens, precedence + 1);
      if (!tokens.hasNext()) {
        return primary;
      }
      if (tokens.peek().is(Tokens.ASC) && Tokens.BINARY_OPERATORS_PRECEDENCE.get(precedence).contains(Tokens.ASC)) {
        return new UnaryExpression(tokens.next(), primary);
      } else if (tokens.peek().is(Tokens.DESC)
          && Tokens.BINARY_OPERATORS_PRECEDENCE.get(precedence).contains(Tokens.DESC)) {
        return new UnaryExpression(tokens.next(), primary);
      } else if (tokens.peek().is(Tokens.IS)
          && Tokens.BINARY_OPERATORS_PRECEDENCE.get(precedence).contains(Tokens.IS)) {
        tokens.next();
        boolean not;
        if (tokens.peek().is(Tokens.NOT)) {
          not = true;
          tokens.next();
        } else {
          not = false;
        }
        assertNext(tokens, Tokens.NULL);
        return new UnaryExpression(
            not ? Token.concat(Tokens.IS, Tokens.NOT, Tokens.NULL) : Token.concat(Tokens.IS, Tokens.NULL), primary);
      } else {
        return parseBinaryExpression(primary, tokens, precedence);
      }
    }
  }

  private Expression parseBinaryExpression(Expression left, PeekingIterator<Token> tokens, int precedence)
      throws ParserException {
    for (;;) {
      Token token = tokens.peek();
      if (!Tokens.BINARY_OPERATORS.contains(token.getString())) {
        return left;
      }
      if (!Tokens.BINARY_OPERATORS_PRECEDENCE.get(precedence).contains(token.getString())) {
        return left;
      }
      tokens.next();
      Expression right = parseExpression(tokens, precedence + 1);
      left = new BinaryExpression(token, left, right);
    }
  }

  private void assertNext(PeekingIterator<Token> tokens, String... values) throws ParserException {
    String next = tokens.next().getString();
    if (!ImmutableSet.copyOf(values).contains(next)) {
      throw new ParserException("Expected " + join(values) + " but got " + next + ".");
    }
  }

  private void assertPeek(PeekingIterator<Token> tokens, String... values) throws ParserException {
    String next = tokens.peek().getString();
    if (!ImmutableSet.copyOf(values).contains(next)) {
      throw new ParserException("Expected " + join(values) + " but got " + next + ".");
    }
  }

  private String join(String[] values) {
    return Stream.of(values).collect(Collectors.joining(", "));
  }

  public static class ParserException extends Exception {
    private static final long serialVersionUID = 1L;

    public ParserException(String msg) {
      super(msg);
    }
  }
}