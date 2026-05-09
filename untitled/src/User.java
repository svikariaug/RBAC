public record User(String username, String fullName, String email) {
    public static User create(String username, String fullName, String email) {
        username = ValidationUtils.normalizeString(username);
        fullName = ValidationUtils.normalizeString(fullName);
        email = ValidationUtils.normalizeString(email);

        ValidationUtils.requireNonEmpty(username, "Username");
        ValidationUtils.requireNonEmpty(fullName, "Full name");
        ValidationUtils.requireNonEmpty(email, "Email");

        if (!ValidationUtils.isValidUsername(username)) {
            throw new IllegalArgumentException("Username must contain only Latin letters, digits, and underscores, and be 3-20 characters long");
        }
        if (!ValidationUtils.isValidEmail(email)) {
            throw new IllegalArgumentException("Email must contain '@' and a dot after it");
        }
        return new User(username, fullName, email);
    }

    public String format() {
        return username + " (" + fullName + ") <" + email + ">";
    }

    public static void main(String[] args) {
        try {
            User user = User.create("user_1", "John Doe", "john@example.com");
            System.out.println("Valid: " + user.format());
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }

        try {
            User.create("ab", "John Doe", "john@example.com");
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid short username: " + e.getMessage());
        }

          try {
            User.create("user-1", "John Doe", "john@example.com");
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid characters: " + e.getMessage());
        }

        try {
            User.create("user_1", "John Doe", "johnexample.com");
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid email: " + e.getMessage());
        }

        try {
            User.create(null, "John Doe", "john@example.com");
        } catch (IllegalArgumentException e) {
            System.out.println("Null username: " + e.getMessage());
        }
    }
}
