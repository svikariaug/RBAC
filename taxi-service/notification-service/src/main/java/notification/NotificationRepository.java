package notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationTask, Long> {

    List<NotificationTask> findByTripIdOrderByIdAsc(Long tripId);
}
