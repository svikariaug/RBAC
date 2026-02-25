import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class TemporaryAssignment extends AbstractRoleAssignment {
    String expiresAt;
    private boolean autoRenew;

    public TemporaryAssignment(User user, Role role, AssignmentMetadata metadata, String expiresAt, boolean autoRenew) {
        super(user, role, metadata);
        this.expiresAt = expiresAt;
        this.autoRenew = autoRenew;
    }

    @Override
    public boolean isActive() {
        return !isExpired();
    }

    @Override
    public String assignmentType() {
        return "TEMPORARY";
    }

    public void extend(String newExpirationDate) {
        this.expiresAt = newExpirationDate;
    }

    public boolean isExpired() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime expiration = LocalDateTime.parse(expiresAt, formatter);
        return LocalDateTime.now().isAfter(expiration);
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