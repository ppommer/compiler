package codegen;

import static interpreter.SteckOperation.*;
import java.util.ArrayList;
import java.util.HashMap;

class PatchLocation {
  public final String functionName;
  public final int argumentCount;
  public final int ldiLocation;

  public PatchLocation(String functionName, int argumentCount, int ldiLocation) {
    this.functionName = functionName;
    this.argumentCount = argumentCount;
    this.ldiLocation = ldiLocation;
  }
}

class FunctionDesc {
  public final int functionIndex;
  public final int argumentCount;

  public FunctionDesc(int functionIndex, int argumentCount) {
    this.functionIndex = functionIndex;
    this.argumentCount = argumentCount;
  }
}

public class CodeGenerationVisitor extends Visitor {
  /*
   * ArrayList for the generated instructions
   */
  private ArrayList<Integer> instructions = new ArrayList<Integer>();
  /*
   * HashMap for local variables. Note that local variables exist
   * only as function parameters and at the beginning of functions;
   * therefore there is only one scope.
   */
  private HashMap<String, Integer> locals = new HashMap<String, Integer>();
  /*
   * Frame cells of the current function. Is needed for the return jump.
   */
  private int frameCells = 0;
  /*
   * Functions that have already been assembled. We remember all functions
   * so that we can patch the call sites in the end.
   */
  private HashMap<String, FunctionDesc> functions = new HashMap<>();
  /*
   * Call sites; function calls must be generated at the very end, 
   * namely when the functions already have known addresses.
   */
  private ArrayList<PatchLocation> patchLocations = new ArrayList<>();

  public int[] getProgram() {
    // Conversion from Integer[] to int[]
    Integer[] program = instructions.toArray(new Integer[0]);
    int[] intProgram = new int[program.length];
    for (int i = 0; i < intProgram.length; i++)
      intProgram[i] = program[i];
    return intProgram;
  }

  private int add(int instruction) {
    int index = instructions.size();
    instructions.add(instruction);
    return index;
  }
  
  private int addDummy() {
    return add(0);
  }
  
  /*
   * Expression
   */

  @Override
  public void visit(Number number) {
    int value = number.getValue();
    add(LDI.encode(value & 0xffff));
    // In case the value needs more than 16 bits, we need several operations.
    // We put the upper 16 bits on the stack, move them to the correct position,
    // and finally combine the upper and lower bits.
    if (value > 0xffff || value < 0) {
      add(LDI.encode(value >>> 16));
      add(SHL.encode(16));
      add(OR.encode());

    }
  }

  @Override
  public void visit(Variable variable) {
    if (!locals.containsKey(variable.getName()))
      throw new RuntimeException("Unknown variable '" + variable.getName() + "'");
    int variableLocation = locals.get(variable.getName());
    add(LDS.encode(variableLocation));
  }

  @Override
  public void visit(Unary unary) {
    switch (unary.getOperator()) {
      case Minus:
        unary.getOperand().accept(this);
        add(LDI.encode(0));
        add(SUB.encode());
        break;
    }
  }

  @Override
  public void visit(Binary binary) {
    binary.getRhs().accept(this);
    binary.getLhs().accept(this);
    switch (binary.getOperator()) {
      case Minus:
        add(SUB.encode());
        break;
      case Plus:
        add(ADD.encode());
        break;
      case MultiplicationOperator:
        add(MUL.encode());
        break;
      case DivisionOperator:
        add(DIV.encode());
        break;
      case Modulo:
        add(MOD.encode());
        break;
    }
  }
  
  @Override
  public void visit(Call call) {
    for (Expression e : call.getArguments())
      e.accept(this);
    int patchLocation = addDummy();
    // We cannot generate the instruction to load the address here yet;
    // the function may be assembled later.
    patchLocations
        .add(new PatchLocation(call.getFunctionName(), call.getArguments().length, patchLocation));
    // Padding for the tail call optimization
    for (int i = 0; i < call.getArguments().length; i++)
      add(NOP.encode());
    add(CALL.encode(call.getArguments().length));
  }
  
  /*
   * Statement
   */

  @Override
  public void visit(Read read) {
    add(IN.encode());
    int location = locals.get(read.getName());
    add(STS.encode(location));
  }

  @Override
  public void visit(Write write) {
    write.getExpression().accept(this);
    add(OUT.encode());
  }

  @Override
  public void visit(Assignment assignment) {
    assignment.getExpression().accept(this);
    Integer location = locals.get(assignment.getName());
    if (location == null)
      throw new RuntimeException("Unknown variable " + assignment.getName());
    add(STS.encode(location));
  }

  @Override
  public void visit(Composite composite) {
    for (Statement s : composite.getStatements())
      s.accept(this);
  }

  @Override
  public void visit(IfThenElse ifThenElse) {
    ifThenElse.getCond().accept(this);
    int jumpToElseAddress = addDummy();
    ifThenElse.getElseBranch().accept(this);
    new True().accept(this);
    int jumpToEndAddress = addDummy();
    // We make the leap to the Else branch
    instructions.set(jumpToElseAddress, JUMP.encode(instructions.size()));
    ifThenElse.getThenBranch().accept(this);
    // We put the jump to the end at the end of the Else branch
    instructions.set(jumpToEndAddress, JUMP.encode(instructions.size()));
  }

  @Override
  public void visit(IfThen ifThen) {
    ifThen.getCond().accept(this);
    add(NOT.encode());
    int jumpToEndAddress = addDummy();
    ifThen.getThenBranch().accept(this);
    instructions.set(jumpToEndAddress, JUMP.encode(instructions.size()));
  }
  
  @Override
  public void visit(While while_) {
    int whileBeginAddress = instructions.size();
    while_.getCond().accept(this);
    add(NOT.encode());
    int jumpToEndAddress = addDummy();
    while_.getBody().accept(this);
    new True().accept(this);
    add(JUMP.encode(whileBeginAddress));
    int whileEndAddress = instructions.size();
    // We put the jump at the end of the loop
    instructions.set(jumpToEndAddress, JUMP.encode(whileEndAddress));
  }

  @Override
  public void visit(Return return_) {
    return_.getExpression().accept(this);
    add(RETURN.encode(frameCells));
  }

  @Override
  public void visit(EmptyStatement emptyStatement) {}
  
  /*
   * Condition
   */

  @Override
  public void visit(True true_) {
    add(LDI.encode(0));
    add(NOT.encode());
  }

  @Override
  public void visit(False false_) {
    add(LDI.encode(0));
  }
  
  @Override
  public void visit(Comparison comparison) {
    comparison.getRhs().accept(this);
    comparison.getLhs().accept(this);
    switch (comparison.getOpeator()) {
      case Equals:
        add(EQ.encode());
        break;
      case NotEquals:
        add(EQ.encode());
        add(NOT.encode());
        break;
      case Greater:
        add(LE.encode());
        add(NOT.encode());
        break;
      case GreaterEqual:
        add(LT.encode());
        add(NOT.encode());
        break;
      case Less:
        add(LT.encode());
        break;
      case LessEqual:
        add(LE.encode());
        break;
    }
  }

  @Override
  public void visit(UnaryCondition unaryCondition) {
    unaryCondition.getOperand().accept(this);
    switch (unaryCondition.getOperator()) {
      case Not:
        add(NOT.encode());
        break;
    }
  }

  @Override
  public void visit(BinaryCondition binaryCondition) {
    binaryCondition.getRhs().accept(this);
    binaryCondition.getLhs().accept(this);
    switch (binaryCondition.getOperator()) {
      case And:
        add(AND.encode());
        break;
      case Or:
        add(OR.encode());
        break;
    }
  }
  
  /*
   * Rest
   */
  
  @Override
  public void visit(Declaration declaration) {
    int offset = locals.size() + 1;
    String[] names = declaration.getNames();
    for (int i = 0; i < names.length; i++) {
      if (locals.containsKey(names[i]))
        throw new RuntimeException("Variable '" + names[i] + "' is already defined");
      locals.put(names[i], offset + i);
    }
    add(ALLOC.encode(names.length));
  }
  
  @Override
  public void visit(Function function) {
    // The beginning of the function has to be marked for the Tail Call Optimization
    add(ALLOC.encode(0));
    int declarations = 0;
    for (Declaration d : function.getDeclarations()) {
      declarations += d.getNames().length;
      d.accept(this);
    }
    String[] parameters = function.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (locals.containsKey(parameters[i]))
        throw new RuntimeException("Variable '" + parameters[i] + "' is already defined");
      locals.put(parameters[i], -parameters.length + i + 1);
    }
    frameCells = parameters.length + declarations;
    for (Statement s : function.getStatements())
      s.accept(this);
  }

  @Override
  public void visit(Program program) {
    // The program starts with a jump to the main function
    int ldiMainAddress = addDummy();
    add(CALL.encode(0));
    add(HALT.encode());
    boolean hasMain = false;
    for (Function f : program.getFunctions()) {
      int functionStartIndex = instructions.size();
      if (f.getName().equals("main")) {
        if(f.getParameters().length > 0)
          throw new RuntimeException("Main function must not have parameters");
        instructions.set(ldiMainAddress, LDI.encode(functionStartIndex));
        hasMain = true;
      }
      functions.put(f.getName(), new FunctionDesc(functionStartIndex, f.getParameters().length));
      locals.clear();
      f.accept(this);
    }
    if(!hasMain)
      throw new RuntimeException("Main function is missing.");
    // After all functions are assembled, we need to patch the instructions
    // that load function addresses.
    for (PatchLocation pl : patchLocations) {
      FunctionDesc fDesc = functions.get(pl.functionName);
      if (fDesc == null)
        throw new RuntimeException("Unknown function " + pl.functionName);
      if (fDesc.argumentCount != pl.argumentCount)
        throw new RuntimeException("Invalid number of function arguments");
      instructions.set(pl.ldiLocation, LDI.encode(fDesc.functionIndex));
    }
  }
}
