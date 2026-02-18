public record Permission(String name, String resource, String description) {
    public Permission {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (resource == null || resource.isEmpty()) {
            throw new IllegalArgumentException("Resource cannot be null or empty");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or empty");
        }
        name = name.toUpperCase().trim();
        if (name.contains(" ")) {
            throw new IllegalArgumentException("Name cannot contain spaces");
        }
        resource = resource.toLowerCase().trim();
    }

    public String format() {
        return name + " on " + resource + ": " + description;
    }

    public boolean matches(String namePattern, String resourcePattern) {
        return name.contains(namePattern.toUpperCase()) && resource.contains(resourcePattern.toLowerCase());
    }
}