package trip;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping("/trips")
    public TripView create(@RequestBody CreateTripBody body) {
        return tripService.create(body);
    }

    @GetMapping("/trips/{id}")
    public TripView get(@PathVariable Long id) {
        return tripService.get(id);
    }

    @GetMapping("/trips")
    public List<TripView> list(@RequestParam("passenger_id") Long passengerId) {
        return tripService.listForPassenger(passengerId);
    }

    @PatchMapping("/trips/{id}/status")
    public TripView patchStatus(@PathVariable Long id, @RequestBody StatusPatchBody body) {
        return tripService.patchStatus(id, body);
    }

    @PatchMapping("/trips/{id}/rating")
    public TripView rate(@PathVariable Long id, @RequestBody RatingBody body) {
        return tripService.rate(id, body);
    }

    @GetMapping("/stats/trips")
    public StatsDayView stats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tripService.statsForDay(date);
    }
}
