package notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shared.RabbitNames;
import shared.TripEventMessage;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String CLAIM_SQL =
            """
            WITH cte AS (
              SELECT id FROM notification_tasks
              WHERE status = 'PENDING' AND attempts < 3
              ORDER BY id
              FOR UPDATE SKIP LOCKED
              LIMIT 1
            )
            UPDATE notification_tasks t
            SET status = 'PROCESSING'
            FROM cte
            WHERE t.id = cte.id
            RETURNING t.id, t.trip_id, t.recipient_type, t.recipient_id, t.message, t.attempts
            """;

    private static final RowMapper<ClaimedRow> CLAIM_MAPPER =
            (rs, rowNum) ->
                    new ClaimedRow(
                            rs.getLong("id"),
                            rs.getLong("trip_id"),
                            rs.getString("recipient_type"),
                            rs.getLong("recipient_id"),
                            rs.getString("message"),
                            rs.getInt("attempts"));

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    public NotificationService(NotificationRepository notificationRepository, JdbcTemplate jdbcTemplate) {
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public NotificationItemView enqueue(NotificationCreateBody body) {
        NotificationTask t = new NotificationTask();
        t.setTripId(body.tripId());
        t.setRecipientType(body.recipientType());
        t.setRecipientId(body.recipientId());
        t.setMessage(body.message());
        t.setStatus(NotificationTask.TaskStatus.PENDING);
        t = notificationRepository.save(t);
        return NotificationItemView.from(t);
    }

    @Transactional(readOnly = true)
    public List<NotificationItemView> listByTrip(Long tripId) {
        return notificationRepository.findByTripIdOrderByIdAsc(tripId).stream()
                .map(NotificationItemView::from)
                .toList();
    }

    @RabbitListener(queues = {RabbitNames.TRIP_NOTIFY_QUEUE})
    @Transactional
    public void onTripEvent(TripEventMessage payload) {
        if (payload.tripId() == null) {
            return;
        }
        if (payload.notificationForPassenger() != null
                && !payload.notificationForPassenger().isBlank()
                && payload.passengerId() != null) {
            NotificationTask t = new NotificationTask();
            t.setTripId(payload.tripId());
            t.setRecipientType(NotificationTask.RecipientType.PASSENGER);
            t.setRecipientId(payload.passengerId());
            t.setMessage(payload.notificationForPassenger());
            t.setStatus(NotificationTask.TaskStatus.PENDING);
            notificationRepository.save(t);
        }
        if (payload.notificationForDriver() != null
                && !payload.notificationForDriver().isBlank()
                && payload.driverId() != null) {
            NotificationTask t = new NotificationTask();
            t.setTripId(payload.tripId());
            t.setRecipientType(NotificationTask.RecipientType.DRIVER);
            t.setRecipientId(payload.driverId());
            t.setMessage(payload.notificationForDriver());
            t.setStatus(NotificationTask.TaskStatus.PENDING);
            notificationRepository.save(t);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ClaimedRow> claimNext() {
        List<ClaimedRow> rows = jdbcTemplate.query(CLAIM_SQL, CLAIM_MAPPER);
        return rows.stream().findFirst();
    }

    public void markSent(long taskId) {
        jdbcTemplate.update("UPDATE notification_tasks SET status = 'SENT' WHERE id = ?", taskId);
    }

    public void markFailure(long taskId) {
        jdbcTemplate.update(
                """
                UPDATE notification_tasks
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= 3 THEN 'FAILED' ELSE 'PENDING' END
                WHERE id = ?
                """,
                taskId);
    }

    public void simulateSend(ClaimedRow task, long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        log.info(
                "[notify] trip={} -> {} {} : {}",
                task.tripId(),
                task.recipientType(),
                task.recipientId(),
                task.message());
    }
}

record NotificationCreateBody(
        Long tripId, NotificationTask.RecipientType recipientType, Long recipientId, String message) {}

record NotificationItemView(
        Long id,
        Long tripId,
        NotificationTask.RecipientType recipientType,
        Long recipientId,
        String message,
        NotificationTask.TaskStatus status,
        int attempts,
        java.time.Instant createdAt) {
    static NotificationItemView from(NotificationTask t) {
        return new NotificationItemView(
                t.getId(),
                t.getTripId(),
                t.getRecipientType(),
                t.getRecipientId(),
                t.getMessage(),
                t.getStatus(),
                t.getAttempts(),
                t.getCreatedAt());
    }
}

record ClaimedRow(long id, long tripId, String recipientType, long recipientId, String message, int attempts) {}
