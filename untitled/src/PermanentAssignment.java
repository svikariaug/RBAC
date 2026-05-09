public class PermanentAssignment extends AbstractRoleAssignment {
    private boolean revoked = false;

    public PermanentAssignment(User user, Role role, AssignmentMetadata metadata) {
        super(user, role, metadata);
    }

    /** Загрузка из снимка. */
    public static PermanentAssignment restoreFromSnapshot(String assignmentId, User user, Role role,
                                                         AssignmentMetadata metadata, boolean revoked) {
        return new PermanentAssignment(assignmentId, user, role, metadata, revoked);
    }

    private PermanentAssignment(String assignmentId, User user, Role role, AssignmentMetadata metadata, boolean revoked) {
        super(user, role, metadata, assignmentId);
        this.revoked = revoked;
    }

    @Override
    public boolean isActive() {
        return !revoked;
    }

    @Override
    public String assignmentType() {
        return "PERMANENT";
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isRevoked() {
        return revoked;
    }
}