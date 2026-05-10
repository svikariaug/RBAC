package user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/auth/login")
    public TokenResponseBody login(@RequestBody LoginRequestBody body) {
        return userService.login(body);
    }

    @PostMapping("/passengers")
    public PassengerView createPassenger(@RequestBody PassengerCreateBody body) {
        return userService.createPassenger(body);
    }

    @GetMapping("/passengers/{id}")
    public PassengerView getPassenger(@PathVariable Long id) {
        return userService.getPassenger(id);
    }

    @PostMapping("/drivers")
    public DriverView createDriver(@RequestBody DriverCreateBody body) {
        return userService.createDriver(body);
    }

    @GetMapping("/drivers/{id}")
    public DriverView getDriver(@PathVariable Long id) {
        return userService.getDriver(id);
    }

    @PatchMapping("/drivers/{id}/status")
    public DriverView patchDriver(@PathVariable Long id, @RequestBody DriverStatusPatch body) {
        return userService.patchDriverStatus(id, body);
    }

    @GetMapping("/drivers/available/cache")
    public List<Long> availableCache() {
        return userService.availableDriverIdsFromCache();
    }

    @GetMapping("/internal/passengers/{id}/exists")
    public void internalPassengerExists(@PathVariable Long id) {
        if (!userService.passengerExists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/internal/drivers/acquire")
    public DriverAcquireResponse internalAcquire() {
        return userService
                .acquireAvailableDriver()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "no available driver"));
    }

    @PatchMapping(value = "/internal/drivers/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void internalDriverStatus(@PathVariable Long id, @RequestBody DriverStatusPatch body) {
        userService.setDriverStatusInternal(
                id, body.status() == null ? Driver.DriverStatus.AVAILABLE : body.status());
    }
}
