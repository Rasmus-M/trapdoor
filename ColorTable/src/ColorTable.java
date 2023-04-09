/*

Convert ZX Spectrum attribute to TI-99/4A color byte.

Spectrum attribute FBPPPIII
- I - Ink colour (0-7)
- P - Paper colour (0-7)
- B - Bright (0-No, 1-Yes)
- F - Flash  (0-No, 1-Yes)

TI color FFFFBBBB

Spectrum palette
0  Black,    1 D Blue,   2 D Red,     3 D Magenta
4  D Green,  5 D Cyan,   6 D Yellow,  7 Grey
8  Black,    9 Blue,    10 Red,      11 Magenta
12 Green,   13 Cyan,    14 Yellow,   15 White

TI palette
0  Trans,    1 Black,    2 M Green,   3 L Green
4  D Blue,   5 L Blue,   6 D Red,     7 Cyan
8  M Red,    9 L Red,   10 D Yellow, 11 L Yellow
12 D Green, 13 Magenta, 14 Gray,     15 White

Not used: 3 L Green, 9 L Red
Not available: D Cyan, D Magenta

 */

public class ColorTable {

    private static final int[] tiPalette = {1,4,6,13,12,7,10,14,1,5,8,13,2,7,11,15};

    public void generate() {
        for (int a = 0; a < 256; a++) {
            int ink = a & 0x07;
            int paper = (a & 0x38) >> 3;
            int bright = (a & 0x40) >> 3; // 0 or 8
            int flash = a & 0x80; // ignore
            int fore = tiPalette[ink + bright];
            int back = tiPalette[paper + bright];
            int b = (fore << 4) | back;
            System.out.print(a % 16 == 0 ? "       byte " : "");
            System.out.print(hexByte(b));
            System.out.print(a % 16 == 15 ? "\n" : ",");
        }
    }

    private String hexByte(int b) {
        String hex = Integer.toHexString(b);
        return ">" + (hex.length() < 2 ? "0" : "") + hex;
    }
}
