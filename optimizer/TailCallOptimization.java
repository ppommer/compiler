package optimizer;

import interpreter.SteckInstruction;
import interpreter.SteckOperation;
import static interpreter.SteckOperation.*;

// Class for counting ALLOC instructions
class AllocCounter {
  private int[] program;

  private int index;

  public int getIndex() {
    return index;
  }

  private int alloced;

  public int getAlloced() {
    return alloced;
  }

  public AllocCounter(int[] program, int index) {
    this.program = program;
    this.index = index;
    this.alloced = 0;
  }

  public void count() {
    while (index < program.length) {
      SteckInstruction insnNext = SteckInstruction.decode(program[index]);
      if (insnNext.getOperation() == SteckOperation.ALLOC) {
        alloced += insnNext.getImmediate();
        index++;
      } else
        break;
    }
  }
}


public class TailCallOptimization {
  public static void optimize(int[] program) {
    int alloced = 0;
    int currentOptAddr = 0;
    while (currentOptAddr < program.length) {
      SteckInstruction insn = SteckInstruction.decode(program[currentOptAddr]);
      optSwitch: switch (insn.getOperation()) {
        case RETURN: {
          // If we detect a RETURN, we may have an end-recursive call
          if (currentOptAddr <= 1)
            break;
          SteckInstruction call = SteckInstruction.decode(program[currentOptAddr - 1]);
          if (call.getOperation() != SteckOperation.CALL)
            // A CALL must have taken place before the return
            break;
          int arguments = call.getImmediate();
          if (currentOptAddr - arguments - 2 < 0)
            // Not enough room, how can that be?
            break;
          // We test whether there is a suitable number of NOPs before the CALL
          for (int i = 0; i < arguments; i++) {
            SteckInstruction insnAtI = SteckInstruction.decode(program[currentOptAddr - 2 - i]);
            if (insnAtI.getOperation() != NOP)
              break optSwitch;
          }
          // We extract the address of the function
          SteckInstruction ldiAddr =
              SteckInstruction.decode(program[currentOptAddr - arguments - 2]);
          if (ldiAddr.getOperation() != SteckOperation.LDI)
            break;
          int fAddr = ldiAddr.getImmediate();
          if (fAddr >= program.length)
            break;
          // We test whether the stack frame of the called function matches the stack frame
          // of the current function
          AllocCounter counter = new AllocCounter(program, fAddr);
          counter.count();
          if (counter.getAlloced() != alloced)
            break;
          if (counter.getAlloced() + arguments != insn.getImmediate())
            break;
          // We move the parameters to the right place.
          fAddr = counter.getIndex();
          int currentArgument = 0;
          for (int i = currentOptAddr - 2 - arguments; i < currentOptAddr - 2; i++)
            program[i] = STS.encode(currentArgument--);
          // We replace the CALL with an unconditional jump
          program[currentOptAddr - 2] = LDI.encode(0);
          program[currentOptAddr - 1] = NOT.encode();
          program[currentOptAddr] = JUMP.encode(fAddr);
        }
        case ALLOC: {
          // If we see an ALLOC, we are at the beginning of the function. We have ensured
          // in the code generator that there is an ALLOC at the beginning of each function.
          AllocCounter counter = new AllocCounter(program, currentOptAddr);
          counter.count();
          // We remember the number of allocated variables.
          alloced = counter.getAlloced();
          currentOptAddr = counter.getIndex() - 1;
          break;
        }
        default: {
          break;
        }
      }
      currentOptAddr++;
    }
  }
}
