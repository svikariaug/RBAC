import java.util.regex.Pattern;

public final class ValidationUtils {
    private ValidationUtils() {}

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATETIME_MIN_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$");
    private static final Pattern DATETIME_SEC_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$");

    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    
    public static boolean isValidDate(String date) {
        if (date == null) return false;
        String d = date.trim();
        return DATE_PATTERN.matcher(d).matches()
                || DATETIME_MIN_PATTERN.matcher(d).matches()
                || DATETIME_SEC_PATTERN.matcher(d).matches();
    }

    public static String normalizeString(String input) {
        if (input == null) return null;
        String s = input.trim().replaceAll("\\s+", " ");
        if (s.contains("@")) {
            return s.toLowerCase();
        }
        return s;
    }

    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }
}

