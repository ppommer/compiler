package parser;

import java.util.ArrayList;
import codegen.Assignment;
import codegen.Bbinop;
import codegen.Binary;
import codegen.BinaryCondition;
import codegen.Binop;
import codegen.Bunop;
import codegen.Call;
import codegen.Comp;
import codegen.Comparison;
import codegen.Composite;
import codegen.Condition;
import codegen.Declaration;
import codegen.Expression;
import codegen.False;
import codegen.Function;
import codegen.IfThen;
import codegen.IfThenElse;
import codegen.Number;
import codegen.Program;
import codegen.Read;
import codegen.Return;
import codegen.Statement;
import codegen.True;
import codegen.Unary;
import codegen.UnaryCondition;
import codegen.Unop;
import codegen.Variable;
import codegen.While;
import codegen.Write;

public class Parser {
  private static final int PADDING = 3;

  public static String[] lex(String input) {
    ArrayList<String> tokens = new ArrayList<>();
    int index = 0;
    while (index < input.length()) {
      char next = input.charAt(index);
      if (next == ' ' || next == '\n' || next == '\r' || next == '\t') {
        index++;
        continue;
      }
      switch (next) {
        case '{':
        case '}':
        case '(':
        case ')':
        case '+':
        case '-':
        case '/':
        case '*':
        case '%':
        case ';':
          index++;
          tokens.add("" + next);
          continue;

      }
      StringBuilder tokenBuilder = new StringBuilder();
      compBinAssLoop: while (index < input.length() && tokenBuilder.length() < 2) {
        char nextOp = input.charAt(index);
        switch (nextOp) {
          case '=':
          case '!':
          case '<':
          case '>':
          case '&':
          case '|':
            tokenBuilder.append(nextOp);
            break;
          default:
            break compBinAssLoop;
        }
        index++;
      }
      if (tokenBuilder.length() == 0) {
        while (index < input.length()) {
          char nextLetterNumber = input.charAt(index);
          if (nextLetterNumber >= 'a' && nextLetterNumber <= 'z'
              || nextLetterNumber >= 'A' && nextLetterNumber <= 'Z'
              || nextLetterNumber >= '0' && nextLetterNumber <= '9')
            tokenBuilder.append(nextLetterNumber);
          else
            break;
          index++;
        }
      }
      if (tokenBuilder.length() > 0)
        tokens.add(tokenBuilder.toString());
      else {
        index++;
        tokens.add("" + next);
      }
    }
    // Padding
    for (int i = 0; i < PADDING; i++)
      tokens.add("");
    return tokens.toArray(new String[0]);
  }

  private String[] tokens;

  private int from;

  public Parser(String[] tokens) {
    this.tokens = tokens;
  }

  public Number parseNumber(String[] program) {
    for (int i = 0; i < program[from].length(); i++) {
      char next = program[from].charAt(i);
      if (!(next >= '0' && next <= '9'))
        return null;
    }
    return new Number(Integer.parseInt(program[from++]));
  }

  private static boolean isKeyword(String token) {
    switch (token) {
      case "true":
      case "false":
      case "int":
      case "while":
      case "if":
      case "else":
      case "read":
      case "write":
      case "return":
        return true;
      default:
        return false;
    }
  }

  private String parseName() {
    if (isKeyword(tokens[from]))
      return null;
    if (tokens[from].length() == 0)
      return null;
    char first = tokens[from].charAt(0);
    if (!(first >= 'a' && first <= 'z') && !(first >= 'A' && first <= 'Z'))
      return null;
    for (int i = 1; i < tokens[from].length(); i++) {
      char next = tokens[from].charAt(i);
      if (!(next >= 'a' && next <= 'z') && !(next >= 'A' && next <= 'Z')
          && !(next >= '0' && next <= '9'))
        return null;
    }
    return tokens[from++];
  }

  private Object parseType() {
    if (tokens[from++].equals("int"))
      return new Object();
    else
      return null;
  }

  private Declaration parseDecl() {
    Object type = parseType();
    if (type == null)
      return null;

    ArrayList<String> variables = new ArrayList<String>();
    while (true) {
      String name = parseName();
      if (name == null)
        return null;
      variables.add(name);
      if (!tokens[from].equals(","))
        break;
      from++;
    }
    if (tokens[from].equals(";")) {
      from += 1;
      return new Declaration(variables.toArray(new String[] {}));
    } else
      return null;
  }

  private Unop parseUnop() {
    if (tokens[from].equals("-")) {
      from = from + 1;
      return Unop.Minus;
    }
    return null;
  }

  private Binop parseBinop() {
    switch (tokens[from]) {
      case "+":
        from++;
        return Binop.Plus;
      case "-":
        from++;
        return Binop.Minus;
      case "*":
        from++;
        return Binop.MultiplicationOperator;
      case "/":
        from++;
        return Binop.DivisionOperator;
      case "%":
        from++;
        return Binop.Modulo;
      default:
        return null;
    }
  }

  private Comp parseComp() {
    switch (tokens[from]) {
      case "==":
        from++;
        return Comp.Equals;
      case "!=":
        from++;
        return Comp.NotEquals;
      case "<=":
        from++;
        return Comp.LessEqual;
      case "<":
        from++;
        return Comp.Less;
      case ">=":
        from++;
        return Comp.GreaterEqual;
      case ">":
        from++;
        return Comp.Greater;
      default:
        return null;
    }
  }

  private Expression parseExpressionNoBinop() {
    // <number>
    int before = from;
    Expression number = parseNumber(tokens);
    if (number != null)
      return number;
    // <name>( ... )
    from = before;
    String functionName = parseName();
    if (functionName != null && tokens[from].equals("(")) {
      from++;
      ArrayList<Expression> arguments = new ArrayList<Expression>();
      if (!tokens[from].equals(")"))
        argumentLoop: while (true) {
          Expression nextArgument = parseExpression();
          if (nextArgument == null)
            return null;
          arguments.add(nextArgument);
          switch (tokens[from]) {
            case ",":
              from++;
              continue;
            case ")":
              break argumentLoop;
            default:
              return null;
          }
        }
      if (!tokens[from++].equals(")"))
        return null;
      return new Call(functionName, arguments.toArray(new Expression[] {}));
    }
    // <name>
    from = before;
    String name = parseName();
    if (name != null)
      return new Variable(name);
    // (<expr>)
    from = before;
    if (tokens[from].equals("(")) {
      from += 1;
      Expression sub = parseExpression();
      if (sub == null)
        return null;
      if (tokens[from].equals(")")) {
        from = from + 1;
        return sub;
      } else
        return null;
    }
    // <unop> <expr>
    from = before;
    Unop unop = parseUnop();
    if (unop != null) {
      Expression sub = parseExpression();
      if (sub != null)
        return new Unary(unop, sub);
      else
        return null;
    }
    return null;
  }

  private Expression parseExpression() {
    Expression current = parseExpressionNoBinop();
    if (current == null)
      return null;

    while (true) {
      int before = from;
      Binop binop = parseBinop();
      if (binop == null) {
        from = before;
        break;
      }
      Expression next = parseExpressionNoBinop();
      if (next == null)
        return null;
      current = new Binary(current, binop, next);
    }

    return current;
  }

  private Bbinop parseBbinop() {
    switch (tokens[from]) {
      case "&&":
        from++;
        return Bbinop.And;
      case "||":
        from++;
        return Bbinop.Or;
      default:
        return null;
    }
  }

  private Bunop parseBunop() {
    if (tokens[from++].equals("!"))
      return Bunop.Not;
    return null;
  }

  private Condition parseConditionNoBinary() {
    // true
    if (tokens[from].equals("true")) {
      from++;
      return new True();
    }
    // false
    if (tokens[from].equals("false")) {
      from++;
      return new False();
    }
    // <expr> <comp> <expr>
    int before = from;
    Expression lhs = parseExpression();
    if (lhs != null) {
      Comp comp = parseComp();
      if (comp == null)
        return null;
      Expression rhs = parseExpression();
      if(rhs == null)
        return null;
      return new Comparison(lhs, comp, rhs);
    }
    // (<cond>)
    from = before;
    if (tokens[from].equals("(")) {
      from += 1;
      Condition sub = parseCondition();
      if (sub == null)
        return null;
      if (tokens[from].equals(")")) {
        from++;
        return sub;
      } else
        return null;
    }
    // <bunop> ( <cond> )
    from = before;
    Bunop bunop = parseBunop();
    if (bunop != null) {
      if (tokens[from].equals("(")) {
        from += 1;
        Condition sub = parseCondition();
        if (sub == null)
          return null;
        if (tokens[from].equals(")")) {
          from++;
          return new UnaryCondition(bunop, sub);
        } else
          return null;
      }
    }
    return null;
  }

  public Condition parseCondition() {
    Condition current = parseConditionNoBinary();
    if (current == null)
      return null;

    while (true) {
      int before = from;
      Bbinop bbinop = parseBbinop();
      if (bbinop == null) {
        from = before;
        break;
      }
      Condition rhs = parseConditionNoBinary();
      if (rhs == null)
        return null;
      current = new BinaryCondition(current, bbinop, rhs);
    }
    return current;
  }

  private Statement parseAssigment() {
    String variableName = parseName();
    if (variableName == null)
      return null;
    if (!tokens[from].equals("="))
      return null;
    from += 1;
    if (from + 2 < tokens.length
        && (tokens[from] + tokens[from + 1] + tokens[from + 2]).equals("read()")) {
      from = from + 3;
      return new Read(variableName);
    }
    Expression exp = parseExpression();
    if (exp == null)
      return null;
    return new Assignment(variableName, exp);
  }

  private Condition parseWhileIteCond() {
    if (tokens[from].equals("("))
      from++;
    else
      return null;
    Condition cond = parseCondition();
    if (cond == null)
      return null;
    if (!tokens[from].equals(")"))
      return null;
    from = from + 1;
    return cond;
  }

  private Statement parseIte() {
    if (!tokens[from].equals("if"))
      return null;
    from++;
    Condition cond = parseWhileIteCond();
    if (cond == null)
      return null;
    Statement thenBranch = parseStatement();
    if (thenBranch == null)
      return null;
    if (from < tokens.length && tokens[from].equals("else")) {
      from++;
      Statement elseBranch = parseStatement();
      if (elseBranch == null)
        return null;
      return new IfThenElse(cond, thenBranch, elseBranch);
    } else
      return new IfThen(cond, thenBranch);
  }

  private Statement parseStatement() {
    switch (tokens[from]) {
      case ";": {
        from = from + 1;
        return new Composite(new Statement[] {});
      }
      case "{": {
        ArrayList<Statement> statements = new ArrayList<Statement>();
        from++;
        while (true) {
          int before = from;
          Statement next = parseStatement();
          if (next == null) {
            from = before;
            break;
          }
          statements.add(next);
        }
        if (tokens[from].equals("}")) {
          from = from + 1;
          return new Composite(statements.toArray(new Statement[] {}));
        } else
          return null;
      }
      case "while": {
        from++;
        Condition cond = parseWhileIteCond();
        if (cond == null)
          return null;
        Statement body = parseStatement();
        if (body == null)
          return null;
        return new While(cond, body);
      }
      case "if": {
        Statement ite = parseIte();
        if (ite == null)
          return null;
        return ite;
      }
      case "write": {
        if (!tokens[from + 1].equals("("))
          return null;
        from += 2;
        Expression exp = parseExpression();
        if (exp == null || !tokens[from].equals(")"))
          return null;
        if (!tokens[from + 1].equals(";"))
          return null;
        from = from + 2;
        return new Write(exp);
      }
      case "return": {
        from++;
        Expression exp = parseExpression();
        if (exp == null)
          return null;
        if (!tokens[from++].equals(";"))
          return null;
        return new Return(exp);
      }
      default: {
        Statement ass = parseAssigment();
        if (ass != null) {
          if (tokens[from].equals(";")) {
            from += 1;
            return ass;
          } else
            return null;
        }
        return null;
      }
    }
  }

  private String[] parseFunctionParameterList() {
    if (!tokens[from++].equals("("))
      return null;
    ArrayList<String> parameters = new ArrayList<String>();
    if (!tokens[from].equals(")")) {
      do {
        if (!tokens[from++].equals("int"))
          return null;
        String parameter = parseName();
        if (parameter == null)
          return null;
        parameters.add(parameter);
      } while (tokens[from++].equals(","));
      from--;
    }
    if (!tokens[from++].equals(")"))
      return null;
    return parameters.toArray(new String[] {});
  }

  private Function parseFunction() {
    if (!tokens[from++].equals("int"))
      return null;
    String functionName = parseName();
    if (functionName == null)
      return null;
    String[] parameters = parseFunctionParameterList();
    if (parameters == null)
      return null;
    if (!tokens[from++].equals("{"))
      return null;
    ArrayList<Declaration> declarations = new ArrayList<Declaration>();
    while (true) {
      int before = from;
      Declaration decl = parseDecl();
      if (decl == null) {
        from = before;
        break;
      }
      declarations.add(decl);
    }
    ArrayList<Statement> statements = new ArrayList<Statement>();
    while (true) {
      int before = from;
      Statement stmt = parseStatement();
      if (stmt == null) {
        from = before;
        break;
      }
      statements.add(stmt);
    }
    if (!tokens[from++].equals("}"))
      return null;
    return new Function(functionName, parameters, declarations.toArray(new Declaration[] {}),
        statements.toArray(new Statement[] {}));
  }

  private Program parseProgram() {
    ArrayList<Function> functions = new ArrayList<Function>();
    int before = from;
    while (true) {
      before = from;
      Function function = parseFunction();
      if (function == null) {
        from = before;
        break;
      } else
        functions.add(function);
    }
    return new Program(functions.toArray(new Function[] {}));
  }

  public Program parse() {
    from = 0;
    Program program = parseProgram();
    // Consume padding
    while (from < this.tokens.length && this.tokens[from].length() == 0)
      from++;
    if (from == this.tokens.length)
      return program;
    return null;
  }
}
