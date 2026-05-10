package trip;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByPassengerIdOrderByCreatedAtDesc(Long passengerId);

    @Query(
            value = """
                    SELECT COUNT(*), COALESCE(AVG(price), 0)
                    FROM trips
                    WHERE CAST(created_at AS date) = CAST(:day AS date)
                    """,
            nativeQuery = true)
    List<Object[]> statsForDay(@Param("day") LocalDate day);
}
