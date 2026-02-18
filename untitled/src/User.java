public record User(String username, String fullName, String email) {
    public static User create(String username, String fullName, String email) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (fullName == null || fullName.isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be null or empty");
        }
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Username must contain only Latin letters, digits, and underscores, and be 3-20 characters long");
        }
        if (!email.matches("^[^@]+@[^@]+\\.[^@]+$")) {
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
