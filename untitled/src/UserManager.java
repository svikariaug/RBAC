import java.util.*;
import java.util.stream.Collectors;

public class UserManager implements Repository<User> {
    private final Map<String, User> usersByUsername = new HashMap<>();

    @Override
    public void add(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (exists(user.username())) {
            throw new IllegalArgumentException("User with username '" + user.username() + "' already exists");
        }
        usersByUsername.put(user.username(), user);
    }

    @Override
    public boolean remove(User user) {
        return usersByUsername.remove(user.username()) != null;
    }

    @Override
    public Optional<User> findById(String id) {
        return findByUsername(id);
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(usersByUsername.values());
    }

    @Override
    public int count() {
        return usersByUsername.size();
    }

    @Override
    public void clear() {
        usersByUsername.clear();
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public Optional<User> findByEmail(String email) {
        return usersByUsername.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst();
    }

    public List<User> findByFilter(UserFilter filter) {
        return usersByUsername.values().stream()
                .filter(filter::test)
                .collect(Collectors.toList());
    }

    public List<User> findAll(UserFilter filter, Comparator<User> sorter) {
        var stream = usersByUsername.values().stream();

        if (filter != null) {
            stream = stream.filter(filter);
        }
        if (sorter != null) {
            stream = stream.sorted(sorter);
        }

        return stream.collect(Collectors.toList());
    }

    public boolean exists(String username) {
        return usersByUsername.containsKey(username);
    }

    public void update(String username, String newFullName, String newEmail) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        User existing = findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + username));

        if (newFullName == null || newFullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be null or blank");
        }
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        User updated = User.create(username, newFullName, newEmail);
        usersByUsername.put(username, updated);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserManager that = (UserManager) o;
        return usersByUsername.equals(that.usersByUsername);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usersByUsername);
    }
}