import java.util.function.Predicate;

@FunctionalInterface
public interface UserFilter extends Predicate<User> {
    @Override
    boolean test(User user);

    default UserFilter and(UserFilter other) {
        return user -> this.test(user) && other.test(user);
    }

    default UserFilter or(UserFilter other) {
        return user -> this.test(user) || other.test(user);
    }
}