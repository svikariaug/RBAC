import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class TemporaryAssignment extends AbstractRoleAssignment {
    String expiresAt;
    private boolean autoRenew;
    private volatile boolean finalizedByScheduler;

    public TemporaryAssignment(User user, Role role, AssignmentMetadata metadata, String expiresAt, boolean autoRenew) {
        super(user, role, metadata);
        initExpires(expiresAt, autoRenew);
    }

    
    public static TemporaryAssignment restoreFromSnapshot(String assignmentId, User user, Role role,
                                                          AssignmentMetadata metadata, String expiresAt, boolean autoRenew) {
        return new TemporaryAssignment(assignmentId, user, role, metadata, expiresAt, autoRenew);
    }

    private TemporaryAssignment(String assignmentId, User user, Role role, AssignmentMetadata metadata,
                                String expiresAt, boolean autoRenew) {
        super(user, role, metadata, assignmentId);
        initExpires(expiresAt, autoRenew);
    }

    private void initExpires(String expiresAt, boolean autoRenew) {
        expiresAt = ValidationUtils.normalizeString(expiresAt);
        if (expiresAt == null || !expiresAt.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            throw new IllegalArgumentException("expiresAt must be in format YYYY-MM-DD HH:MM");
        }
        this.expiresAt = expiresAt;
        this.autoRenew = autoRenew;
        if ("2000-01-01 00:00".equals(this.expiresAt)) {
            finalizedByScheduler = true;
        }
    }

    @Override
    public boolean isActive() {
        return !isExpired();
    }

    @Override
    public String assignmentType() {
        return "TEMPORARY";
    }

    public synchronized void extend(String newExpirationDate) {
        newExpirationDate = ValidationUtils.normalizeString(newExpirationDate);
        if (newExpirationDate == null || !newExpirationDate.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            throw new IllegalArgumentException("expiresAt must be in format YYYY-MM-DD HH:MM");
        }
        this.expiresAt = newExpirationDate;
        finalizedByScheduler = false;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    
    public synchronized boolean finalizeExpirationIfDue() {
        if (finalizedByScheduler) {
            return false;
        }
        if (!isExpired()) {
            return false;
        }
        finalizedByScheduler = true;
        expiresAt = "2000-01-01 00:00";
        return true;
    }

    public boolean isExpired() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String now = LocalDateTime.now().format(formatter);
        return DateUtils.isAfter(now, expiresAt);
    }

    public String getTimeRemaining() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime expiration = LocalDateTime.parse(expiresAt, formatter);
        Duration duration = Duration.between(LocalDateTime.now(), expiration);
        if (duration.isNegative()) {
            return "Expired";
        }
        return duration.toDays() + " days, " + duration.toHoursPart() + " hours, " + duration.toMinutesPart() + " minutes";
    }

    @Override
    public String summary() {
        return super.summary() + "\nExpires at: " + expiresAt + "\nTime remaining: " + getTimeRemaining();
    }
}