import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkoolConverter {

    private final static Pattern commentReferencePattern = Pattern.compile("#R([0-9]{5})");
    private final static Pattern lsbRegisterPattern = Pattern.compile("[cel]");

    private Set<Integer> references;
    private List<Integer> labels;

    public void convert(File skoolFile, File tms9900File, int startAddress) throws IOException  {
        List<Z80Line> z80Lines = readSkoolFile(skoolFile);
        System.out.println(z80Lines.size() + " lines read from: " + skoolFile.getPath());
        references = findReferences(z80Lines);
        labels = findLabels(z80Lines);
        List<TMS9900Line> tms9900Lines = convert(z80Lines, startAddress);
        writeTMS9900File(tms9900File, tms9900Lines);
        System.out.println(tms9900Lines.size() + " lines written to: " + tms9900File.getPath());
    }

    private Set<Integer> findReferences(List<Z80Line> z80Lines) {
        Set<Integer> references = new HashSet<>();
        for (Z80Line z80Line : z80Lines) {
            switch (z80Line.getType()) {
                case Instruction:
                    Instruction instruction = new Instruction(z80Line.getInstruction());
                    Operand opr1 = instruction.getOpr1();
                    if (opr1 != null && opr1.isWordOperand() && (opr1.getType() == Operand.Type.Immediate || opr1.getType() == Operand.Type.Indirect)) {
                        references.add(opr1.getValue());
                    }
                    Operand opr2 = instruction.getOpr2();
                    if (opr2 != null && opr2.isWordOperand() && (opr2.getType() == Operand.Type.Immediate || opr2.getType() == Operand.Type.Indirect)) {
                        references.add(opr2.getValue());
                    }
                    break;
                case Data:
                    if (z80Line.getInstruction().startsWith("DEFW")) {
                        String[] words = z80Line.getInstruction().substring(4).trim().split(",");
                        for (String word : words) {
                            int addr = Integer.parseInt(word);
                            if (addr >= 16384) {
                                references.add(addr);
                            }
                        }
                    }
                case Comment:
                case ContinuationComment:
                    String comment = z80Line.getComment();
                    if (comment != null) {
                        Matcher matcher = commentReferencePattern.matcher(z80Line.getComment());
                        while (matcher.find()) {
                            references.add(Integer.parseInt(matcher.group(1)));
                        }
                    }
                    break;
            }
        }
        return references;
    }

    private List<Integer> findLabels(List<Z80Line> z80Lines) {
        List<Integer> labels = new ArrayList<>();
        for (Z80Line z80Line : z80Lines) {
            if (z80Line.getType() == Z80Line.Type.Data || z80Line.getType() == Z80Line.Type.Instruction) {
                if (references.contains(z80Line.getAddress())) {
                    labels.add(z80Line.getAddress());
                }
            }
        }
        return labels;
    }

    private List<TMS9900Line> convert(List<Z80Line> z80Lines, int startAddress) {
        List<TMS9900Line> tms9900Lines = new ArrayList<>();
        int i = 0;
        while (i < z80Lines.size()) {
            Z80Line z80Line = z80Lines.get(i);
            switch (z80Line.getType()) {
                case Empty:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Empty));
                    break;
                case Directive:
                    break;
                case Comment:
                    tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Comment, z80Line.getComment()));
                    break;
                case ContinuationComment:
                    int j = i;
                    Z80Line previousLine = j > 0 ? z80Lines.get(--j) : null;
                    while (previousLine != null && previousLine.getType() == Z80Line.Type.ContinuationComment && j > 0) {
                        previousLine = z80Lines.get(--j);
                    }
                    Z80Line.Type previousLineType = previousLine != null ? previousLine.getType() : null;
                    tms9900Lines.add(new TMS9900Line(previousLineType == Z80Line.Type.Data ? TMS9900Line.Type.ContinuationCommentData : TMS9900Line.Type.ContinuationCommentInstruction, z80Line.getComment()));
                    break;
                case Data:
                case Instruction:
                    if (z80Line.getAddress() >= startAddress) {
                        if (references.contains(z80Line.getAddress())) {
                            boolean lastLineWasALabel = lastLineWasALabel(tms9900Lines);
                            TMS9900Line label = new TMS9900Line(TMS9900Line.Type.Label);
                            label.setLabel(Util.getLabel(z80Line.getAddress()));
                            tms9900Lines.add(label);
                            if (lastLineWasALabel) {
                                TMS9900Line directive = new TMS9900Line(TMS9900Line.Type.Directive);
                                directive.setDirective("equ  $");
                                tms9900Lines.add(directive);
                            }
                        }
                        if (z80Line.getType() == Z80Line.Type.Data) {
                            i += convertData(z80Line, tms9900Lines);
                        } else {
                            i += convertInstruction(z80Line, tms9900Lines);
                        }
                    }
                    break;
            }
            i++;
        }
        return tms9900Lines;
    }

    private int convertData(Z80Line z80Line, List<TMS9900Line> tms9900Lines) {
        String instruction;
        if (z80Line.getInstruction().startsWith("DEFM")) {
            String message = "";
            String[] messageParts = z80Line.getInstruction().substring(4).trim().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String messagePart : messageParts) {
                if (messagePart.startsWith("\"")) {
                    message += messagePart.substring(1, messagePart.length() - 1).replace("\"", "").replace("'", "'");
                } else {
                    message += (char) Integer.parseInt(messagePart);
                }
            }
            instruction = "text '" + message + "'";
        } else {
            boolean isWord = z80Line.getInstruction().startsWith("DEFW");
            instruction = z80Line.getInstruction().replace("DEFB", "byte").replace("DEFW", "data");
            if (isWord) {
                String[] words = instruction.substring(4).trim().split(",");
                for (String word : words) {
                    if (!word.isEmpty() && references.contains(Integer.parseInt(word))) {
                        instruction = instruction.replace(word, getValidLabel(Integer.parseInt(word)));
                    }
                }
            } else {
                if (z80Line.getComment() != null) {
                    Matcher matcher = commentReferencePattern.matcher(z80Line.getComment());
                    while (matcher.find()) {
                        int address = Integer.parseInt(matcher.group(1));
                        int msb = address / 256;
                        int lsb = address % 256;
                        String z80Bytes = lsb + "," + msb;
                        String tms9900Bytes = Util.getLabel(address) + "%256," + Util.getLabel(address) + "/256";
                        instruction = instruction.replace(z80Bytes, tms9900Bytes);
                    }
                }
            }
        }
        tms9900Lines.add(new TMS9900Line(TMS9900Line.Type.Data, z80Line.getComment(), instruction));
        return 0;
    }

    private int convertInstruction(Z80Line z80Line, List<TMS9900Line> tms9900Lines) {
        Instruction instruction = new Instruction(z80Line.getInstruction());
        Operand opr1 = instruction.getOpr1();
        Operand opr2 = instruction.getOpr2();
        TMS9900Line tms9900Line = new TMS9900Line(TMS9900Line.Type.Instruction, z80Line.getComment());
        tms9900Line.setZ80Instruction(z80Line.getInstruction());
        List<TMS9900Line> additionalLines = new ArrayList<>();
        switch (instruction.getOpcode()) {
            case "add":
                tms9900Line.setInstruction("a    " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                break;
            case "and":
                tms9900Line.setInstruction("andi a," + opr1.getValue() + "*256");
                break;
            case "bit":
                tms9900Line.setInstruction("mov  " + getTMS9900Equivalent(opr2) + ",tmp0");
                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "andi tmp0," + (1 << opr1.getValue())));
                break;
            case "call":
                if (opr1 != null && opr2 == null) {
                    tms9900Line.setInstruction(".call @" + getTMS9900Equivalent(opr1));
                } else if (opr1 != null && opr1.getType() == Operand.Type.Flag) {
                    setInverseJumpInstruction(tms9900Line, tms9900Lines, opr1);
                    additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, ".call @" + getTMS9900Equivalent(opr2)));
                    TMS9900Line label = new TMS9900Line(TMS9900Line.Type.Label);
                    label.setLabel("!");
                    additionalLines.add(label);
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "cp":
                tms9900Line.setInstruction("cb   a," + getTMS9900Equivalent(opr1));
                break;
            case "dec":
                if (opr1 != null) {
                    boolean isWord = opr1.isWordOperand();
                    if (isWord) {
                        tms9900Line.setInstruction("dec  " + getTMS9900Equivalent(opr1));
                    } else {
                        tms9900Line.setInstruction("sb   one," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "di":
                tms9900Line.setInstruction("limi 0");
                break;
            case "djnz":
                tms9900Line.setInstruction("sb   one,b");
                additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "jne  " + getTMS9900Equivalent(opr1)));
                break;
            case "ex":
                tms9900Line.setInstruction(".ex_" + getTMS9900Equivalent(opr1) + "_" + getTMS9900Equivalent(opr2));
                break;
            case "exx":
                tms9900Line.setInstruction(".exx");
                break;
            case "inc":
                if (opr1 != null) {
                    boolean isWord = opr1.isWordOperand();
                    if (isWord) {
                        tms9900Line.setInstruction("inc  " + getTMS9900Equivalent(opr1));
                    } else {
                        tms9900Line.setInstruction("ab   one," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "jp":
                if (opr1 != null && opr2 == null) {
                    if (opr1.getValue() != 0) {
                        tms9900Line.setInstruction("b    @" + getTMS9900Equivalent(opr1));
                    } else {
                        tms9900Line.setInstruction("b    " + opr1.getValue());
                        tms9900Line.setComment("TODO. ");
                    }
                } else if (opr1 != null && opr1.getType() == Operand.Type.Flag) {
                    setInverseJumpInstruction(tms9900Line, tms9900Lines, opr1);
                    additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "b    @" + getTMS9900Equivalent(opr2)));
                    TMS9900Line label = new TMS9900Line(TMS9900Line.Type.Label);
                    label.setLabel("!");
                    additionalLines.add(label);
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "jr":
                if (opr1 != null && opr2 == null) {
                    tms9900Line.setInstruction("jmp  " + getTMS9900Equivalent(opr1));
                } else if (opr1 != null && opr1.getType() == Operand.Type.Flag) {
                    String flag = opr1.getFlag();
                    if ((flag.equals("c") || flag.equals("nc")) && getLastInstruction(tms9900Lines).startsWith("cb")) {
                        if (flag.equals("c")) {
                            tms9900Line.setInstruction("jl   " + getTMS9900Equivalent(opr2));
                        } else {
                            tms9900Line.setInstruction("jhe  " + getTMS9900Equivalent(opr2));
                        }
                    } else {
                        tms9900Line.setInstruction("j" + getTMS9900Equivalent(opr1) + "  " + getTMS9900Equivalent(opr2));
                        if (flag.equals("c") || flag.equals("nc")) {
                            tms9900Line.setComment("TODO: check code. " + tms9900Line.getComment());
                        }
                    }
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "ld":
                if (opr1 != null && opr2 != null) {
                    boolean isWord = opr1.isWordOperand();
                    if (opr2.getType() == Operand.Type.Immediate) {
                        if (isWord) {
                            tms9900Line.setInstruction("li   " + getTMS9900Equivalent(opr1) + "," + getTMS9900Equivalent(opr2, isWord));
                        } else {
                            tms9900Line.setInstruction("movb " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                        }
                    } else {
                        tms9900Line.setInstruction((isWord ? "mov " : "movb") + " " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "ldir":
                tms9900Line.setInstruction(".ldir");
                break;
            case "neg":
                tms9900Line.setInstruction("neg a");
                break;
            case "nop":
                tms9900Line.setInstruction("nop");
                break;
            case "or":
                tms9900Line.setInstruction("socb " + getTMS9900Equivalent(opr1) + ",a");
                break;
            case "push":
                tms9900Line.setInstruction(".push " + getTMS9900Equivalent(opr1));
                break;
            case "pop":
                tms9900Line.setInstruction(".pop " + getTMS9900Equivalent(opr1));
                break;
            case "res":
                tms9900Line.setInstruction("szcb @bits+" + opr1.getValue() + "," + getTMS9900Equivalent(opr2));
                break;
            case "ret":
                if (opr1 == null) {
                    tms9900Line.setInstruction(".ret");
                } else if (opr1.getType() == Operand.Type.Flag) {
                    setInverseJumpInstruction(tms9900Line, tms9900Lines, opr1);
                    additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, ".ret"));
                    TMS9900Line label = new TMS9900Line(TMS9900Line.Type.Label);
                    label.setLabel("!");
                    additionalLines.add(label);
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "rlc":
                tms9900Line.setInstruction("sra  " + getTMS9900Equivalent(opr1) + ",1");
                break;
            case "set":
                tms9900Line.setInstruction("socb @bits+" + opr1.getValue() + "," + getTMS9900Equivalent(opr2));
                break;
            case "sub":
                tms9900Line.setInstruction("sb   " + getTMS9900Equivalent(opr1) + ",a");
                break;
            case "sbc":
                tms9900Line.setInstruction("sb   " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                break;
            case "srl":
                if (!lsbRegisterPattern.matcher(opr1.getRegister()).matches()) {
                    tms9900Line.setInstruction("srl  " + getTMS9900Equivalent(opr1) + ",1");
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "xor":
                if (opr1.getType() == Operand.Type.Register && opr1.getRegister().equals("a")) {
                    tms9900Line.setInstruction("sb   a,a");
                } else if (opr1.getType() == Operand.Type.Immediate) {
                    if (opr1.getValue() == 1) {
                        tms9900Line.setInstruction("xor  one,a");
                    } else {
                        tms9900Line.setInstruction("li   tmp0," + opr1.getValue());
                        additionalLines.add(new TMS9900Line(TMS9900Line.Type.Instruction, null, "xor  tmp0,a"));
                    }
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            default:
                tms9900Line.setInstruction("; " + instruction);
                break;
        }
        tms9900Lines.add(tms9900Line);
        tms9900Lines.addAll(additionalLines);
        return 0;
    }

    private void setInverseJumpInstruction(TMS9900Line tms9900Line, List<TMS9900Line> tms9900Lines, Operand opr1) {
        String flag = opr1.getFlag();
        if ((flag.equals("c") || flag.equals("nc")) && getLastInstruction(tms9900Lines).startsWith("cb")) {
            if (flag.equals("c")) {
                tms9900Line.setInstruction("jhe  !");
            } else {
                tms9900Line.setInstruction("jl   !");
            }
        } else {
            switch (flag) {
                case "z":
                    tms9900Line.setInstruction("jne  !");
                    break;
                case "nz":
                    tms9900Line.setInstruction("jeq  !");
                    break;
                case "c":
                    tms9900Line.setInstruction("jnc  !");
                    tms9900Line.setComment("TODO: check code. " + tms9900Line.getComment());
                    break;
                case "nc":
                    tms9900Line.setInstruction("joc  !");
                    tms9900Line.setComment("TODO: check code. " + tms9900Line.getComment());
                    break;
            }
        }
    }

    private String getTMS9900Equivalent(Operand operand) {
        return getTMS9900Equivalent(operand, operand.isWordOperand());
    }

    private String getTMS9900Equivalent(Operand operand, boolean isWord) {
        if (operand == null) {
            return "?";
        }
        switch (operand.getType()) {
            case Immediate:
                if (!isWord) {
                    if (operand.getValue() == 1) {
                        return "one";
                    } else if (operand.getValue() == -1) {
                        return "mone";
                    } else {
                        return "@bytes+" + operand.getValue();
                    }
                } else {
                    if (operand.getValue() < 16384) {
                        return Integer.toString(operand.getValue());
                    } else {
                        return getValidLabel(operand.getValue());
                    }
                }
            case Register:
                String reg = operand.getRegister();
                return (lsbRegisterPattern.matcher(reg).matches() ? "@" : "") + reg;
            case Indirect:
                return "@" + getValidLabel(operand.getValue());
            case IndirectRegister:
                return "*" + operand.getRegister();
            case Indexed:
                if (operand.getValue() != 0) {
                    return "@" + operand.getValue() + "(" + operand.getRegister() + ")";
                } else {
                    return "*" + operand.getRegister();
                }
            case Flag:
                switch (operand.getFlag()) {
                    case "z":
                        return "eq";
                    case "nz":
                        return "ne";
                    case "c":
                        return "oc";
                    case "nc":
                        return "nc";
                    default:
                        return "";
                }
            default:
                return operand.getOperand();
        }
    }

    private String getValidLabel(int address) {
        for (int i = 0; i < labels.size(); i++) {
            int addr = labels.get(i);
            if (addr == address) {
                return Util.getLabel(address);
            }
            if (addr > address) {
                if (i > 0) {
                    int previousAddr = labels.get(i - 1);
                    return Util.getLabel(previousAddr) + "+" + (address - previousAddr);
                } else {
                    return Integer.toString(address);
                }
            }
        }
        return Integer.toString(address);
    }

    private String getLastInstruction(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            if (tms9900Line.getType() == TMS9900Line.Type.Instruction) {
                return tms9900Line.getInstruction();
            }
        }
        return "";
    }

    private boolean lastLineWasALabel(List<TMS9900Line> tms9900Lines) {
        for (int i = tms9900Lines.size() - 1; i >= 0; i--) {
            TMS9900Line tms9900Line = tms9900Lines.get(i);
            TMS9900Line.Type type = tms9900Line.getType();
            if (type == TMS9900Line.Type.Label) {
                return true;
            } else if (type == TMS9900Line.Type.Instruction || type == TMS9900Line.Type.Data) {
                return false;
            }
        }
        return false;
    }

    private List<Z80Line> readSkoolFile(File skoolFile) throws IOException {
        List<Z80Line> z80Lines = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(skoolFile));
        String line = reader.readLine();
        while (line != null) {
            z80Lines.add(new Z80Line(line));
            line = reader.readLine();
        }
        return z80Lines;
    }

    private void writeTMS9900File(File tms9900File, List<TMS9900Line> tms9900Lines) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(tms9900File));
        for (TMS9900Line tms9900Line : tms9900Lines) {
            writer.write(tms9900Line.toString());
            writer.newLine();
        }
        writer.close();
    }
}
