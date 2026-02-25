import java.util.function.Predicate;

@FunctionalInterface
public interface RoleFilter extends Predicate<Role> {
    @Override
    boolean test(Role role);

    default RoleFilter and(RoleFilter other) {
        return role -> this.test(role) && other.test(role);
    }

    default RoleFilter or(RoleFilter other) {
        return role -> this.test(role) || other.test(role);
    }
}