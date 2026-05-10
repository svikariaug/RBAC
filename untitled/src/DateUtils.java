import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateUtils {
    private DateUtils() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATETIME_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String getCurrentDate() {
        return LocalDate.now().format(DATE);
    }

    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DATETIME_SEC);
    }

    public static boolean isBefore(String date1, String date2) {
        if (!ValidationUtils.isValidDate(date1) || !ValidationUtils.isValidDate(date2)) {
            throw new IllegalArgumentException("Invalid date format");
        }
        return normalizeComparable(date1).compareTo(normalizeComparable(date2)) < 0;
    }

    public static boolean isAfter(String date1, String date2) {
        if (!ValidationUtils.isValidDate(date1) || !ValidationUtils.isValidDate(date2)) {
            throw new IllegalArgumentException("Invalid date format");
        }
        return normalizeComparable(date1).compareTo(normalizeComparable(date2)) > 0;
    }

    
    public static String addDays(String date, int days) {
        ValidationUtils.requireNonEmpty(date, "date");
        String d = date.trim();

        if (d.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            LocalDate ld = LocalDate.parse(d, DATE);
            return ld.plusDays(days).format(DATE);
        }
        if (d.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            LocalDateTime ldt = LocalDateTime.parse(d, DATETIME_MIN);
            return ldt.plusDays(days).format(DATETIME_MIN);
        }
        if (d.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) {
            LocalDateTime ldt = LocalDateTime.parse(d, DATETIME_SEC);
            return ldt.plusDays(days).format(DATETIME_SEC);
        }
        throw new IllegalArgumentException("Invalid date format: " + date);
    }

    
    public static String formatRelativeTime(String date) {
        ValidationUtils.requireNonEmpty(date, "date");
        String d = date.trim();

        LocalDateTime target;
        if (d.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            target = LocalDate.parse(d, DATE).atStartOfDay();
        } else if (d.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            target = LocalDateTime.parse(d, DATETIME_MIN);
        } else if (d.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) {
            target = LocalDateTime.parse(d, DATETIME_SEC);
        } else {
            throw new IllegalArgumentException("Invalid date format: " + date);
        }

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, target);
        long seconds = duration.getSeconds();

        if (seconds == 0) return "now";
        boolean future = seconds > 0;
        long absSeconds = Math.abs(seconds);

        long days = absSeconds / (24 * 3600);
        long hours = (absSeconds % (24 * 3600)) / 3600;
        long minutes = (absSeconds % 3600) / 60;

        String unit;
        long value;
        if (days > 0) {
            unit = "days";
            value = days;
        } else if (hours > 0) {
            unit = "hours";
            value = hours;
        } else {
            unit = "minutes";
            value = Math.max(1, minutes);
        }

        return future ? ("in " + value + " " + unit) : (value + " " + unit + " ago");
    }

    private static String normalizeComparable(String date) {
        String d = date.trim();
        if (d.length() == 10) { 
            return d;
        }
        if (d.length() == 16) { 
            return d;
        }
        if (d.length() == 19) { 
            return d;
        }
        throw new IllegalArgumentException("Invalid date format: " + date);
    }
}

