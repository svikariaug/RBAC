package user;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final String REDIS_AVAILABLE = "taxi:available-drivers";
    private static final long JWT_SECONDS = 3600;

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final StringRedisTemplate redis;

    public UserService(
            UserRepository userRepository,
            DriverRepository driverRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.redis = redis;
    }

    @Transactional
    public PassengerView createPassenger(PassengerCreateBody body) {
        if (body.password() == null || body.password().isBlank()) {
            throw new IllegalArgumentException("password required");
        }
        Passenger p = new Passenger();
        p.setName(body.name());
        p.setEmail(body.email());
        p.setPhone(body.phone());
        p.setPasswordHash(passwordEncoder.encode(body.password()));
        p = userRepository.save(p);
        return PassengerView.from(p);
    }

    @Transactional(readOnly = true)
    public PassengerView getPassenger(Long id) {
        Passenger p = userRepository.findById(id).orElseThrow(() -> new NotFoundException("passenger not found"));
        return PassengerView.from(p);
    }

    public boolean passengerExists(Long id) {
        return userRepository.existsById(id);
    }

    @Transactional
    public DriverView createDriver(DriverCreateBody body) {
        if (body.password() == null || body.password().isBlank()) {
            throw new IllegalArgumentException("password required");
        }
        Driver d = new Driver();
        d.setName(body.name());
        d.setEmail(body.email());
        d.setPhone(body.phone());
        d.setLicenseNumber(body.licenseNumber());
        d.setPasswordHash(passwordEncoder.encode(body.password()));
        d.setStatus(Driver.DriverStatus.AVAILABLE);
        d = driverRepository.save(d);
        refreshDriverCache();
        return DriverView.from(d);
    }

    @Transactional(readOnly = true)
    public DriverView getDriver(Long id) {
        Driver d = driverRepository.findById(id).orElseThrow(() -> new NotFoundException("driver not found"));
        return DriverView.from(d);
    }

    @Transactional
    public DriverView patchDriverStatus(Long id, DriverStatusPatch patch) {
        Driver d = driverRepository.findById(id).orElseThrow(() -> new NotFoundException("driver not found"));
        d.setStatus(patch.status());
        d = driverRepository.save(d);
        refreshDriverCache();
        return DriverView.from(d);
    }

    @Transactional
    public Optional<DriverAcquireResponse> acquireAvailableDriver() {
        Optional<Driver> opt = driverRepository.lockOneAvailableForUpdate();
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Driver d = opt.get();
        d.setStatus(Driver.DriverStatus.BUSY);
        driverRepository.save(d);
        refreshDriverCache();
        return Optional.of(new DriverAcquireResponse(d.getId(), d.getName(), d.getStatus()));
    }

    @Transactional
    public void setDriverStatusInternal(Long driverId, Driver.DriverStatus status) {
        Driver d = driverRepository.findById(driverId).orElseThrow(() -> new NotFoundException("driver not found"));
        d.setStatus(status);
        driverRepository.save(d);
        refreshDriverCache();
    }

    public List<Long> availableDriverIdsFromCache() {
        Set<String> members = redis.opsForSet().members(REDIS_AVAILABLE);
        if (members == null || members.isEmpty()) {
            refreshDriverCache();
            members = redis.opsForSet().members(REDIS_AVAILABLE);
        }
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return members.stream().map(Long::parseLong).sorted().collect(Collectors.toList());
    }

    public void warmCache() {
        refreshDriverCache();
    }

    public TokenResponseBody login(LoginRequestBody req) {
        String role = req.role() == null ? "PASSENGER" : req.role().toUpperCase();
        String subject;
        if ("DRIVER".equals(role)) {
            Driver d = driverRepository
                    .findByEmail(req.email())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
            if (!passwordEncoder.matches(req.password(), d.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
            }
            subject = "driver:" + d.getId();
        } else {
            Passenger p = userRepository
                    .findByEmail(req.email())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
            if (!passwordEncoder.matches(req.password(), p.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
            }
            subject = "passenger:" + p.getId();
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("taxi-user")
                .issuedAt(now)
                .expiresAt(now.plus(JWT_SECONDS, ChronoUnit.SECONDS))
                .subject(subject)
                .claim("role", role)
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponseBody(token, "Bearer", JWT_SECONDS);
    }

    private void refreshDriverCache() {
        redis.delete(REDIS_AVAILABLE);
        List<Long> ids = driverRepository.findAllByStatus(Driver.DriverStatus.AVAILABLE).stream()
                .map(Driver::getId)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            redis.opsForSet().add(REDIS_AVAILABLE, ids.stream().map(String::valueOf).toArray(String[]::new));
        }
    }
}

record PassengerCreateBody(String name, String email, String phone, String password) {}

record DriverCreateBody(String name, String email, String phone, String licenseNumber, String password) {}

record LoginRequestBody(String email, String password, String role) {}

record TokenResponseBody(String accessToken, String tokenType, long expiresInSeconds) {}

record DriverStatusPatch(Driver.DriverStatus status) {}

record PassengerView(Long id, String name, String email, String phone, Instant createdAt) {
    static PassengerView from(Passenger p) {
        return new PassengerView(p.getId(), p.getName(), p.getEmail(), p.getPhone(), p.getCreatedAt());
    }
}

record DriverView(
        Long id, String name, String email, String phone, String licenseNumber, Driver.DriverStatus status,
        Instant createdAt) {
    static DriverView from(Driver d) {
        return new DriverView(
                d.getId(), d.getName(), d.getEmail(), d.getPhone(), d.getLicenseNumber(), d.getStatus(),
                d.getCreatedAt());
    }
}

record DriverAcquireResponse(Long id, String name, Driver.DriverStatus status) {}

class NotFoundException extends RuntimeException {
    NotFoundException(String message) {
        super(message);
    }
}
