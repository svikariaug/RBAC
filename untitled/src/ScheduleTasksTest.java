import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleTasksTest {

    private RBACSystem system;

    @BeforeEach
    void setUp() {
        system = new RBACSystem(0);
    }

    @AfterEach
    void tearDown() {
        system.shutdownAsyncServices();
    }

    @Test
    @DisplayName("Истёкшее временное назначение финализируется один раз")
    void finalizeTemporaryOnce() {
        User u = User.create("exp1", "Expired one", "e1@t.com");
        Role r = new Role("R_TMP", "Temp role");
        system.getUserManager().add(u);
        system.getRoleManager().add(r);

        TemporaryAssignment ta = new TemporaryAssignment(u, r, AssignmentMetadata.now("admin", "t"),
                "2000-01-02 12:00", false);
        system.getAssignmentManager().add(ta);

        assertTrue(ta.isExpired());
        assertEquals(1, system.getAssignmentManager().finalizeExpiredTemporaryAssignments());
        assertEquals(0, system.getAssignmentManager().finalizeExpiredTemporaryAssignments());
        assertEquals("2000-01-01 00:00", ta.getExpiresAt());
    }

    @Test
    @DisplayName("Тик планировщика пишет SCHEDULER_TICK в audit log")
    void maintenanceTickWritesAudit() {
        system.initialize();
        ScheduledMaintenanceTask.runMaintenanceTick(system);
        assertTrue(system.getAuditLog().flush(2, TimeUnit.SECONDS));
        boolean found = system.getAuditLog().getByAction("SCHEDULER_TICK").stream()
                .anyMatch(e -> e.details().contains("closedTemp="));
        assertTrue(found);
    }

    @Test
    @DisplayName("При schedulerPeriodSeconds=0 фонового планировщика нет")
    void noSchedulerWhenPeriodZero() {
        assertNull(system.getScheduledMaintenance());
    }
}
