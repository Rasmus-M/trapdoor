public class TMS9900Line {

    public enum Type {
        Empty,
        Label,
        Comment,
        ContinuationCommentData,
        ContinuationCommentInstruction,
        Data,
        Instruction
    }

    private final Type type;
    private String label;
    private String instruction;
    private String z80Instruction;
    private String comment;

    public TMS9900Line(Type type) {
        this.type = type;
    }

    public TMS9900Line(Type type, String comment) {
        this.type = type;
        this.comment = comment;
    }

    public TMS9900Line(Type type, String comment, String instruction) {
        this.type = type;
        this.comment = comment;
        this.instruction = instruction;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public void setZ80Instruction(String z80Instruction) {
        this.z80Instruction = z80Instruction;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String toString() {
        switch (type) {
            case Empty:
                return "";
            case Label:
                return label + ":";
            case Comment:
                return "* " + comment;
            case ContinuationCommentData:
                return Util.space(69) + "; " + comment;
            case ContinuationCommentInstruction:
                return Util.space(59) + "; " + comment;
            case Data:
                String dataCommentIndent = Util.space(Math.max(69 - 7 - instruction.length(), 1));
                return Util.space(7) + instruction + (comment != null ? dataCommentIndent + "; " + comment : "");
            case Instruction:
                String commentIndent = Util.space(Math.max(39 - 7 - instruction.length(), 1));
                String combinedComment = Util.fit(z80Instruction != null ? z80Instruction : "", 17) + " " + (comment != null ? "; " + comment : "");
                return Util.space(7) + instruction + commentIndent + "; " + combinedComment;
        }
        return "";
    }
}
