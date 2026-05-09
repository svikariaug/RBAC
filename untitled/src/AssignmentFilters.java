import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AssignmentFilters {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static AssignmentFilter byUser(User user) {
        return assignment -> assignment.user().equals(user);
    }

    public static AssignmentFilter byUsername(String username) {
        return assignment -> assignment.user().username().equals(username);
    }

    public static AssignmentFilter byRole(Role role) {
        return assignment -> assignment.role().equals(role);
    }

    public static AssignmentFilter byRoleName(String roleName) {
        if (roleName == null) return assignment -> false;
        String key = roleName.trim().toUpperCase();
        return assignment -> assignment.role().getName().equalsIgnoreCase(key);
    }

    public static AssignmentFilter activeOnly() {
        return RoleAssignment::isActive;
    }

    public static AssignmentFilter inactiveOnly() {
        return assignment -> !assignment.isActive();
    }

    public static AssignmentFilter byType(String type) {
        return assignment -> assignment.assignmentType().equals(type);
    }

    public static AssignmentFilter assignedBy(String username) {
        return assignment -> assignment.metadata().assignedBy().equals(username);
    }

    public static AssignmentFilter assignedAfter(String date) {
        return assignment -> {
            LocalDateTime assignedAt = LocalDateTime.parse(assignment.metadata().assignedAt(), FMT);
            LocalDateTime compareDate = LocalDateTime.parse(date + " 00:00", FMT);
            return assignedAt.isAfter(compareDate);
        };
    }

    public static AssignmentFilter expiringBefore(String date) {
        return assignment -> {
            if (assignment instanceof TemporaryAssignment ta) {
                LocalDateTime expires = LocalDateTime.parse(ta.expiresAt, FMT);
                LocalDateTime compareDate = LocalDateTime.parse(date + " 23:59", FMT);
                return expires.isBefore(compareDate);
            }
            return false;
        };
    }
}