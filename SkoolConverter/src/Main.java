import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        new SkoolConverter().convert(
            new File("ZX-Spectrum/through-the-trap-door.skool"),
            new File("src/trap-door.a99")
        );
    }
}
