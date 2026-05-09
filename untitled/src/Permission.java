public record Permission(String name, String resource, String description) {
    public Permission {
        name = ValidationUtils.normalizeString(name);
        resource = ValidationUtils.normalizeString(resource);
        description = ValidationUtils.normalizeString(description);

        ValidationUtils.requireNonEmpty(name, "Name");
        ValidationUtils.requireNonEmpty(resource, "Resource");
        ValidationUtils.requireNonEmpty(description, "Description");

        name = name.toUpperCase();
        if (name.contains(" ")) {
            throw new IllegalArgumentException("Name cannot contain spaces");
        }
        resource = resource.toLowerCase();
    }

    public String format() {
        return name + " on " + resource + ": " + description;
    }

    public boolean matches(String namePattern, String resourcePattern) {
        return name.contains(namePattern.toUpperCase()) && resource.contains(resourcePattern.toLowerCase());
    }
}