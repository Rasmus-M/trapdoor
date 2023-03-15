public class Util {

    public static String getLabel(int address) {
        return "_" + address;
    }

    public static String space(int n) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < n; i++) {
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    public static String fit(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n);
        } else if (s.length() < n) {
            return s + space(n - s.length());
        } else {
            return s;
        }
    }
}
