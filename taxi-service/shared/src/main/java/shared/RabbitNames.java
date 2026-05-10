package shared;

public final class RabbitNames {

    private RabbitNames() {}

    public static final String TRIP_EVENTS_EXCHANGE = "taxi.trip.events";
    public static final String TRIP_NOTIFY_QUEUE = "taxi.trip.notifications";
    public static final String TRIP_NOTIFY_ROUTING_KEY = "trip.notification";
}
