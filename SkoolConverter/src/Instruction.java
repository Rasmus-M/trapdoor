public class Instruction {

    private String instruction;
    private String opcode;
    private String operands;
    private Operand opr1;
    private Operand opr2;

    public Instruction(String instruction) {
        this.instruction = instruction;
        String[] instrParts = instruction.toLowerCase().split(" ", 2);
        opcode = instrParts[0];
        operands = instrParts.length > 1 ? instrParts[1] : null;
        String[] argParts = operands != null ? operands.split(",", 2) : null;
        opr1 = argParts != null ? new Operand(argParts[0], opcode) : null;
        opr2 = argParts != null && argParts.length > 1 ? new Operand(argParts[1], opcode) : null;
    }

    public String getOpcode() {
        return opcode;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getOperands() {
        return operands;
    }

    public Operand getOpr1() {
        return opr1;
    }

    public Operand getOpr2() {
        return opr2;
    }

    public String toString() {
        return instruction;
    }
}
