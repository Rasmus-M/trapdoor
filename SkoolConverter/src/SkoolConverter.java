import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkoolConverter {

    private final static Pattern commentReferencePattern = Pattern.compile("#R([0-9]{5})");

    public void convert(File skoolFile, File tms9900File) throws IOException  {
        List<Z80Line> z80Lines = readSkoolFile(skoolFile);
        System.out.println(z80Lines.size() + " lines read from: " + skoolFile.getPath());
        Set<Integer> references = findReferences(z80Lines);
        List<TMS9900Line> tms9900Lines = convert(z80Lines, references);
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

    private List<TMS9900Line> convert(List<Z80Line> z80Lines, Set<Integer> references) {
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
                    Z80Line previousLine = i > 0 ? z80Lines.get(i - 1) : null;
                    Z80Line.Type previousLineType = previousLine != null ? previousLine.getType() : null;
                    tms9900Lines.add(new TMS9900Line(previousLineType == Z80Line.Type.Data ? TMS9900Line.Type.ContinuationCommentData : TMS9900Line.Type.ContinuationCommentInstruction, z80Line.getComment()));
                    break;
                case Data:
                case Instruction:
                    if (references.contains(z80Line.getAddress())) {
                        TMS9900Line label = new TMS9900Line(TMS9900Line.Type.Label);
                        label.setLabel(Util.getLabel(z80Line.getAddress()));
                        tms9900Lines.add(label);
                    }
                    if (z80Line.getType() == Z80Line.Type.Data) {
                        i += convertData(z80Line, tms9900Lines);
                    } else {
                        i += convertInstruction(z80Line, tms9900Lines);
                    }
                    break;
            }
            i++;
        }
        return tms9900Lines;
    }

    private int convertData(Z80Line z80Line, List<TMS9900Line> tms9900Lines) {
        String instruction = z80Line.getInstruction().replace("DEFB", "byte").replace("DEFW", "data");
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
            case "call":
                if (opr1 != null && opr2 == null) {
                    tms9900Line.setInstruction("bl @" + Util.getLabel(opr1.getValue()));
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "dec":
                if (opr1 != null) {
                    boolean isWord = opr1.isWordOperand();
                    if (isWord) {
                        tms9900Line.setInstruction("dec " + getTMS9900Equivalent(opr1));
                    } else {
                        tms9900Line.setInstruction("sb one," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "inc":
                if (opr1 != null) {
                    boolean isWord = opr1.isWordOperand();
                    if (isWord) {
                        tms9900Line.setInstruction("inc " + getTMS9900Equivalent(opr1));
                    } else {
                        tms9900Line.setInstruction("ab one," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "jp":
                if (opr1 != null && opr2 == null) {
                    tms9900Line.setInstruction("b @" + Util.getLabel(opr1.getValue()));
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "jr":
                if (opr1 != null && opr2 == null) {
                    tms9900Line.setInstruction("jmp " + Util.getLabel(opr1.getValue()));
                } else {
                    tms9900Line.setInstruction("; " + instruction);
                }
                break;
            case "ld":
                if (opr1 != null && opr2 != null) {
                    boolean isWord = opr1.isWordOperand();
                    boolean isImmediate = opr2.getType() == Operand.Type.Immediate;
                    if (isImmediate) {
                        if (isWord) {
                            tms9900Line.setInstruction("li " + getTMS9900Equivalent(opr1) + "," + getTMS9900Equivalent(opr2, isWord));
                        } else {
                            tms9900Line.setInstruction("movb " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                        }
                    } else {
                        tms9900Line.setInstruction((isWord ? "mov" : "movb") + " " + getTMS9900Equivalent(opr2) + "," + getTMS9900Equivalent(opr1));
                    }
                }
                break;
            case "ret":
                if (opr1 == null) {
                    tms9900Line.setInstruction("rt");
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

    public String getTMS9900Equivalent(Operand operand) {
        return getTMS9900Equivalent(operand, operand.isWordOperand());
    }

    public String getTMS9900Equivalent(Operand operand, boolean isWord) {
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
                        return Util.getLabel(operand.getValue());
                    }
                }
            case Register:
                return operand.getRegister();
            case Indirect:
                return "@" + Util.getLabel(operand.getValue());
            case IndirectRegister:
                return "*" + operand.getRegister();
            case Indexed:
                if (operand.getValue() != 0) {
                    return "@" + operand.getValue() + "(" + operand.getRegister() + ")";
                } else {
                    return "*" + operand.getRegister();
                }
            default:
                return operand.getOperand();
        }
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
