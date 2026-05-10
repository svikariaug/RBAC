import java.util.function.Predicate;

@FunctionalInterface
public interface AssignmentFilter extends Predicate<RoleAssignment> {
    @Override
    boolean test(RoleAssignment assignment);

    default AssignmentFilter and(AssignmentFilter other) {
        return assignment -> this.test(assignment) && other.test(assignment);
    }

    default AssignmentFilter or(AssignmentFilter other) {
        return assignment -> this.test(assignment) || other.test(assignment);
    }
}