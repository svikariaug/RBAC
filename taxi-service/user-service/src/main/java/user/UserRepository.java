package user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<Passenger, Long> {

    Optional<Passenger> findByEmail(String email);
}

interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByEmail(String email);

    List<Driver> findAllByStatus(Driver.DriverStatus status);

    @Query(
            value = """
                    SELECT * FROM drivers
                    WHERE status = 'AVAILABLE'
                    ORDER BY id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
            nativeQuery = true)
    Optional<Driver> lockOneAvailableForUpdate();
}
