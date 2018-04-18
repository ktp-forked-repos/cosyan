package com.cosyan.db.lang.sql;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.cosyan.db.lang.sql.Tokens.FloatToken;
import com.cosyan.db.lang.sql.Tokens.IntToken;
import com.cosyan.db.lang.sql.Tokens.StringToken;
import com.cosyan.db.lang.sql.Tokens.Token;
import com.cosyan.db.session.IParser.ParserException;
import com.google.common.collect.ImmutableList;

public class LexerTest {

  private Lexer lexer = new Lexer();

  @Test
  public void testBinaryExpressions() throws ParserException {
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("+", null), new Token("b", null), new Token(";", null)),
        lexer.tokens("a+b;"));
    assertEquals(
        ImmutableList.of(new Token("xyz", null), new Token("<", null), new Token("abc", null), new Token(";", null)),
        lexer.tokens("xyz<abc;"));
    assertEquals(
        ImmutableList.of(new Token("xyz", null), new Token("<=", null), new Token("abc", null), new Token(";", null)),
        lexer.tokens("xyz<=abc;"));
  }

  @Test
  public void testLiterals() throws ParserException {
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("+", null), new StringToken("b", null), new Token(";", null)),
        lexer.tokens("a+'b';"));
    assertEquals(
        ImmutableList.of(new Token("xyz", null), new Token("+", null), new StringToken("b", null),
            new Token(";", null)),
        lexer.tokens("\"xyz\"+b;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("<", null), new IntToken("100", null), new Token(";", null)),
        lexer.tokens("a<100;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("<", null), new FloatToken("1.5", null), new Token(";", null)),
        lexer.tokens("a<1.5;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("<", null), new FloatToken("-1.5", null),
            new Token(";", null)),
        lexer.tokens("a<-1.5;"));
    assertEquals(
        ImmutableList.of(new IntToken("3", null), new Token("+", null), new IntToken("-1", null), new Token(";", null)),
        lexer.tokens("3+-1;"));
  }

  @Test
  public void testWhitespaces() throws ParserException {
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("+", null), new Token("b", null), new Token(";", null)),
        lexer.tokens("a + b;"));
    assertEquals(
        ImmutableList.of(new Token("xyz", null), new Token("<", null), new Token("abc", null), new Token(";", null)),
        lexer.tokens("xyz     < \nabc;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("+", null), new StringToken("b", null), new Token(";", null)),
        lexer.tokens("a+  'b';"));
    assertEquals(
        ImmutableList.of(new Token("xyz", null), new Token("+", null), new StringToken("b", null),
            new Token(";", null)),
        lexer.tokens("\"xyz\"  +\nb;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("<", null), new IntToken("100", null), new Token(";", null)),
        lexer.tokens("a< 100 ;"));
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token("<", null), new FloatToken("1.5", null), new Token(";", null)),
        lexer.tokens("a < 1.5;"));
  }

  @Test
  public void testIdents() throws ParserException {
    assertEquals(
        ImmutableList.of(new Token("a", null), new Token(".", null), new Token("b", null), new Token(";", null)),
        lexer.tokens("a.b;"));
    assertEquals(
        ImmutableList.of(new Token("f", null), new Token("(", null), new Token(")", null), new Token(";", null)),
        lexer.tokens("f();"));
    assertEquals(
        ImmutableList.of(new Token("f", null), new Token(".", null), new Token("g", null), new Token("(", null),
            new Token(")", null), new Token(";", null)),
        lexer.tokens("f.g();"));
    assertEquals(
        ImmutableList.of(new Token("f", null), new Token("(", null), new Token(")", null), new Token(".", null),
            new Token("g", null), new Token(";", null)),
        lexer.tokens("f().g;"));
  }
}
