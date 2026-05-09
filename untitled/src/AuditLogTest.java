import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {

    @Test
    void logAndFilter() {
        AuditLog log = new AuditLog();
        log.log("USER_CREATE", "admin", "john", "created");
        log.log("ROLE_CREATE", "admin", "MANAGER", "created");
        log.log("USER_DELETE", "system", "x", "deleted");

        assertEquals(3, log.getAll().size());
        assertEquals(2, log.getByPerformer("admin").size());
        assertEquals(1, log.getByAction("USER_DELETE").size());
    }
}

