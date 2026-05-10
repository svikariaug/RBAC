package shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripEventMessage(
        Long tripId,
        Long passengerId,
        Long driverId,
        String status,
        String notificationForPassenger,
        String notificationForDriver) {}
