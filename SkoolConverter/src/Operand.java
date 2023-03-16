import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Operand {

    private static final Pattern immediatePattern = Pattern.compile("^[0-9]{1,5}$");
    private static final Pattern registerPattern = Pattern.compile("^(a|af|b|c|bc|d|e|de|h|l|hl|ix|iy|sp)$");
    private static final Pattern indirectPattern = Pattern.compile("^\\(([0-9]{1,5})\\)$");
    private static final Pattern indirectRegisterPattern = Pattern.compile("^\\((bc|de|hl|ix|iy|sp)\\)$");
    private static final Pattern indexedPattern = Pattern.compile("^\\((ix|iy)([+-][0-9]{1,3})\\)$");
    private static final Pattern flagPattern = Pattern.compile("^(nz|z|c|nc)$");

    private static final Pattern wordPattern = Pattern.compile("^(bc|de|hl|ix|iy|sp|\\(?[0-9]{4,5}\\)?)$");

    public enum Type {
        Immediate,
        Register,
        Indirect,
        IndirectRegister,
        Indexed,
        Relative,
        Flag,
        Other
    }

    private final String operand;
    private final Type type;
    private int value;
    private String register;
    private String flag;

    public Operand(String operand, String opcode) {
        this.operand = operand;
        Matcher m;
        m = immediatePattern.matcher(operand);
        if (m.find()) {
            type = Type.Immediate;
            value = Integer.parseInt(operand);
            return;
        }
        if (!opcode.equals("jr") && !opcode.equals("jp") && !opcode.equals("ret")) {
            m = registerPattern.matcher(operand);
            if (m.find()) {
                type = Type.Register;
                register = operand;
                return;
            }
        }
        m = indirectPattern.matcher(operand);
        if (m.find()) {
            type = Type.Indirect;
            value = Integer.parseInt(m.group(1));
            return;
        }
        m = indirectRegisterPattern.matcher(operand);
        if (m.find()) {
            type = Type.IndirectRegister;
            register = m.group(1);
            return;
        }
        m = indexedPattern.matcher(operand);
        if (m.find()) {
            type = Type.Indexed;
            register = m.group(1);
            value = Integer.parseInt(m.group(2));
            return;
        }
        m = flagPattern.matcher(operand);
        if (m.find()) {
            type = Type.Flag;
            flag = m.group(1);
            return;
        }
        type = Type.Other;
    }

    public boolean isByteOperand() {
        return !isWordOperand();
    }

    public boolean isWordOperand() {
        return wordPattern.matcher(this.operand).matches();
    }

    public Type getType() {
        return type;
    }

    public String getOperand() {
        return operand;
    }

    public int getValue() {
        return value;
    }

    public String getRegister() {
        return register;
    }

    public String getFlag() {
        return flag;
    }
}
