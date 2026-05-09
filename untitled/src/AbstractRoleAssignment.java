import java.util.UUID;

public abstract class AbstractRoleAssignment implements RoleAssignment {
    private final String assignmentId;
    private final User user;
    private final Role role;
    private final AssignmentMetadata metadata;

    public AbstractRoleAssignment(User user, Role role, AssignmentMetadata metadata) {
        this(user, role, metadata, null);
    }

    /**
     * @param fixedAssignmentId если не {@code null} и не пусто — используется при загрузке снимка
     */
    protected AbstractRoleAssignment(User user, Role role, AssignmentMetadata metadata, String fixedAssignmentId) {
        if (fixedAssignmentId != null && !fixedAssignmentId.isBlank()) {
            this.assignmentId = fixedAssignmentId.trim();
        } else {
            this.assignmentId = UUID.randomUUID().toString();
        }
        this.user = user;
        this.role = role;
        this.metadata = metadata;
    }

    @Override
    public String assignmentId() {
        return assignmentId;
    }

    @Override
    public User user() {
        return user;
    }

    @Override
    public Role role() {
        return role;
    }

    @Override
    public AssignmentMetadata metadata() {
        return metadata;
    }

    @Override
    public abstract boolean isActive();

    @Override
    public abstract String assignmentType();

    public String summary() {
        String status = isActive() ? "ACTIVE" : "INACTIVE";
        return "[" + assignmentType() + "] " + role.getName() + " assigned to " + user.username() + " by " + metadata.assignedBy() + " at " + metadata.assignedAt() + "\n" +
                "Reason: " + (metadata.reason() != null ? metadata.reason() : "None") + "\n" +
                "Status: " + status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractRoleAssignment that = (AbstractRoleAssignment) o;
        return assignmentId.equals(that.assignmentId);
    }

    @Override
    public int hashCode() {
        return assignmentId.hashCode();
    }
}