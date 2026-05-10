import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AssignmentSorters {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static Comparator<RoleAssignment> byUsername() {
        return Comparator.comparing(assignment -> assignment.user().username());
    }

    public static Comparator<RoleAssignment> byRoleName() {
        return Comparator.comparing(assignment -> assignment.role().getName());
    }

    public static Comparator<RoleAssignment> byAssignmentDate() {
        return Comparator.comparing(assignment -> LocalDateTime.parse(assignment.metadata().assignedAt(), FMT));
    }
}