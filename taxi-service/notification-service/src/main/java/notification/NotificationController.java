package notification;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/notifications")
    public NotificationItemView create(@RequestBody NotificationCreateBody body) {
        return notificationService.enqueue(body);
    }

    @GetMapping("/notifications")
    public List<NotificationItemView> list(@RequestParam("trip_id") Long tripId) {
        return notificationService.listByTrip(tripId);
    }
}
