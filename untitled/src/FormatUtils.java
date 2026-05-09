import java.util.ArrayList;
import java.util.List;

public final class FormatUtils {
    private FormatUtils() {}

    public static String formatTable(String[] headers, List<String[]> rows) {
        if (headers == null) throw new IllegalArgumentException("headers cannot be null");
        if (rows == null) rows = List.of();

        int cols = headers.length;
        List<String[]> safeRows = new ArrayList<>();
        for (String[] r : rows) {
            if (r == null) continue;
            if (r.length != cols) {
                throw new IllegalArgumentException("Row column count mismatch");
            }
            safeRows.add(r);
        }

        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers[i] == null ? 0 : headers[i].length();
        }
        for (String[] r : safeRows) {
            for (int i = 0; i < cols; i++) {
                String cell = r[i] == null ? "" : r[i];
                widths[i] = Math.max(widths[i], cell.length());
            }
        }

        StringBuilder sb = new StringBuilder();
        String border = buildBorder(widths);
        sb.append(border);
        sb.append("|");
        for (int i = 0; i < cols; i++) {
            sb.append(" ").append(padRight(headers[i] == null ? "" : headers[i], widths[i])).append(" |");
        }
        sb.append("\n");
        sb.append(border);
        for (String[] r : safeRows) {
            sb.append("|");
            for (int i = 0; i < cols; i++) {
                String cell = r[i] == null ? "" : r[i];
                sb.append(" ").append(padRight(cell, widths[i])).append(" |");
            }
            sb.append("\n");
        }
        sb.append(border);
        return sb.toString();
    }

    public static String formatBox(String text) {
        String t = text == null ? "" : text;
        String[] lines = t.split("\\R", -1);
        int width = 0;
        for (String line : lines) width = Math.max(width, line.length());

        String top = "+" + "-".repeat(width + 2) + "+\n";
        StringBuilder sb = new StringBuilder();
        sb.append(top);
        for (String line : lines) {
            sb.append("| ").append(padRight(line, width)).append(" |\n");
        }
        sb.append(top);
        return sb.toString();
    }

    public static String formatHeader(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "";
        return "\n" + t + "\n" + "=".repeat(t.length()) + "\n";
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) return null;
        if (maxLength < 0) throw new IllegalArgumentException("maxLength must be >= 0");
        if (text.length() <= maxLength) return text;
        if (maxLength <= 3) return ".".repeat(maxLength);
        return text.substring(0, maxLength - 3) + "...";
    }

    public static String padRight(String text, int length) {
        String s = text == null ? "" : text;
        if (s.length() >= length) return s;
        return s + " ".repeat(length - s.length());
    }

    public static String padLeft(String text, int length) {
        String s = text == null ? "" : text;
        if (s.length() >= length) return s;
        return " ".repeat(length - s.length()) + s;
    }

    private static String buildBorder(int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        sb.append("\n");
        return sb.toString();
    }
}

