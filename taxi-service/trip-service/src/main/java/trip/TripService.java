package trip;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import shared.RabbitNames;
import shared.TripEventMessage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class TripService {

    private static final double EARTH_KM = 6371.0;

    private final TripRepository tripRepository;
    private final RestClient userClient;
    private final RabbitTemplate rabbitTemplate;
    private final BigDecimal tariffPerKm;

    public TripService(
            TripRepository tripRepository,
            RestClient userServiceRestClient,
            RabbitTemplate rabbitTemplate,
            @Value("${taxi.trip.tariff-per-km}") double tariffPerKm) {
        this.tripRepository = tripRepository;
        this.userClient = userServiceRestClient;
        this.rabbitTemplate = rabbitTemplate;
        this.tariffPerKm = BigDecimal.valueOf(tariffPerKm);
    }

    @Transactional
    public TripView create(CreateTripBody req) {
        ensurePassenger(req.passengerId());
        UserAcquireResponse driver = acquireDriver();

        double distance = haversineKm(req.originLat(), req.originLon(), req.destLat(), req.destLon());
        if (distance < 0.1) {
            distance = 0.1;
        }
        BigDecimal price =
                tariffPerKm.multiply(BigDecimal.valueOf(distance)).setScale(2, RoundingMode.HALF_UP);

        Trip t = new Trip();
        t.setPassengerId(req.passengerId());
        t.setDriverId(driver.id());
        t.setStatus(Trip.TripStatus.ASSIGNED);
        t.setOrigin(req.origin());
        t.setDestination(req.destination());
        t.setOriginLat(req.originLat());
        t.setOriginLon(req.originLon());
        t.setDestLat(req.destLat());
        t.setDestLon(req.destLon());
        t.setDistanceKm(distance);
        t.setPrice(price);
        t = tripRepository.save(t);

        publish(
                new TripEventMessage(
                        t.getId(),
                        t.getPassengerId(),
                        t.getDriverId(),
                        t.getStatus().name(),
                        "Поездка #" + t.getId() + " назначена. Водитель: " + driver.name(),
                        "Вам назначена поездка #" + t.getId() + " от " + t.getOrigin() + " до " + t.getDestination()));
        return TripView.from(t);
    }

    @Transactional(readOnly = true)
    public TripView get(Long id) {
        Trip t = tripRepository.findById(id).orElseThrow(() -> new TripNotFoundException("trip not found"));
        return TripView.from(t);
    }

    @Transactional(readOnly = true)
    public List<TripView> listForPassenger(Long passengerId) {
        return tripRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
                .map(TripView::from)
                .toList();
    }

    @Transactional
    public TripView patchStatus(Long id, StatusPatchBody patch) {
        Trip t = tripRepository.findById(id).orElseThrow(() -> new TripNotFoundException("trip not found"));
        Trip.TripStatus next = patch.status();
        assertTransition(t.getStatus(), next);
        t.setStatus(next);
        syncDriver(t);
        t = tripRepository.save(t);
        publish(
                new TripEventMessage(
                        t.getId(),
                        t.getPassengerId(),
                        t.getDriverId(),
                        t.getStatus().name(),
                        "Статус поездки #" + t.getId() + ": " + t.getStatus(),
                        "Статус поездки #" + t.getId() + ": " + t.getStatus()));
        return TripView.from(t);
    }

    @Transactional
    public TripView rate(Long id, RatingBody rating) {
        if (rating.stars() < 1 || rating.stars() > 5) {
            throw new IllegalArgumentException("rating must be 1..5");
        }
        Trip t = tripRepository.findById(id).orElseThrow(() -> new TripNotFoundException("trip not found"));
        if (t.getStatus() != Trip.TripStatus.COMPLETED) {
            throw new IllegalStateException("only completed trips can be rated");
        }
        t.setRating((short) rating.stars());
        t = tripRepository.save(t);
        return TripView.from(t);
    }

    @Transactional(readOnly = true)
    public StatsDayView statsForDay(LocalDate day) {
        List<Object[]> rows = tripRepository.statsForDay(day);
        if (rows.isEmpty()) {
            return new StatsDayView(day, 0L, BigDecimal.ZERO.setScale(2));
        }
        Object[] r = rows.get(0);
        long count = ((Number) r[0]).longValue();
        BigDecimal avg = new BigDecimal(r[1].toString()).setScale(2, RoundingMode.HALF_UP);
        return new StatsDayView(day, count, avg);
    }

    private void publish(TripEventMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitNames.TRIP_EVENTS_EXCHANGE, RabbitNames.TRIP_NOTIFY_ROUTING_KEY, message);
    }

    private void ensurePassenger(Long passengerId) {
        try {
            userClient.get().uri("/internal/passengers/{id}/exists", passengerId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new UserServiceClientException("passenger not found");
            }
            throw e;
        }
    }

    private UserAcquireResponse acquireDriver() {
        try {
            return userClient
                    .post()
                    .uri("/internal/drivers/acquire")
                    .retrieve()
                    .body(UserAcquireResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new UserServiceClientException("no available driver");
            }
            throw e;
        }
    }

    private void patchDriverRemote(Long driverId, String status) {
        userClient
                .patch()
                .uri("/internal/drivers/{id}/status", driverId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserStatusPatchBody(status))
                .retrieve()
                .toBodilessEntity();
    }

    private void syncDriver(Trip t) {
        if (t.getDriverId() == null) {
            return;
        }
        Trip.TripStatus s = t.getStatus();
        if (s == Trip.TripStatus.COMPLETED || s == Trip.TripStatus.CANCELLED) {
            patchDriverRemote(t.getDriverId(), "AVAILABLE");
        } else if (s == Trip.TripStatus.ASSIGNED
                || s == Trip.TripStatus.ACCEPTED
                || s == Trip.TripStatus.IN_PROGRESS) {
            patchDriverRemote(t.getDriverId(), "BUSY");
        }
    }

    private static void assertTransition(Trip.TripStatus cur, Trip.TripStatus next) {
        boolean ok =
                switch (cur) {
                    case ASSIGNED -> next == Trip.TripStatus.ACCEPTED || next == Trip.TripStatus.CANCELLED;
                    case ACCEPTED -> next == Trip.TripStatus.IN_PROGRESS || next == Trip.TripStatus.CANCELLED;
                    case IN_PROGRESS -> next == Trip.TripStatus.COMPLETED || next == Trip.TripStatus.CANCELLED;
                    case PENDING -> next == Trip.TripStatus.CANCELLED;
                    default -> false;
                };
        if (!ok) {
            throw new IllegalStateException("invalid status transition " + cur + " -> " + next);
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_KM * c;
    }
}

record CreateTripBody(
        Long passengerId,
        String origin,
        String destination,
        double originLat,
        double originLon,
        double destLat,
        double destLon) {}

record StatusPatchBody(Trip.TripStatus status) {}

record RatingBody(int stars) {}

record TripView(
        Long id,
        Long passengerId,
        Long driverId,
        Trip.TripStatus status,
        String origin,
        String destination,
        double distanceKm,
        BigDecimal price,
        Short rating,
        java.time.Instant createdAt,
        java.time.Instant updatedAt) {
    static TripView from(Trip t) {
        return new TripView(
                t.getId(),
                t.getPassengerId(),
                t.getDriverId(),
                t.getStatus(),
                t.getOrigin(),
                t.getDestination(),
                t.getDistanceKm(),
                t.getPrice(),
                t.getRating(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}

record StatsDayView(LocalDate date, long tripCount, BigDecimal averagePrice) {}

record UserAcquireResponse(Long id, String name, String status) {}

record UserStatusPatchBody(String status) {}

class TripNotFoundException extends RuntimeException {
    TripNotFoundException(String message) {
        super(message);
    }
}

class UserServiceClientException extends RuntimeException {
    UserServiceClientException(String message) {
        super(message);
    }
}
