import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentManagerTest {

    private UserManager userManager;
    private RoleManager roleManager;
    private AssignmentManager manager;

    @BeforeEach
    void setUp() {
        userManager = new UserManager();
        roleManager = new RoleManager();
        manager = new AssignmentManager(userManager, roleManager);
        roleManager.setAssignmentManager(manager);
    }

    @Test
    void add_shouldAddAssignment() {
        User u = User.create("dev1", "Dev One", "dev1@ex.com");
        Role r = new Role("Developer", "Dev role");
        userManager.add(u);
        roleManager.add(r);

        AssignmentMetadata meta = AssignmentMetadata.now("admin", "Test");
        String future = "2026-12-31 23:59";
        RoleAssignment ass = new TemporaryAssignment(u, r, meta, future, false);

        manager.add(ass);

        assertEquals(1, manager.count());
        assertTrue(manager.userHasRole(u, r));
    }

    @Test
    void add_duplicateRoleForUser_shouldThrow() {
        User u = User.create("usr", "User", "u@ex.com");
        Role r = new Role("RoleA", "Role A");
        userManager.add(u);
        roleManager.add(r);

        AssignmentMetadata meta = AssignmentMetadata.now("admin", null);
        RoleAssignment a1 = new PermanentAssignment(u, r, meta);
        RoleAssignment a2 = new PermanentAssignment(u, r, meta);

        manager.add(a1);
        assertThrows(IllegalArgumentException.class, () -> manager.add(a2));
    }

    @Test
    void getUserPermissions_shouldAggregateFromAllRoles() {
        User u = User.create("multi", "Multi", "m@ex.com");
        userManager.add(u);

        Role r1 = new Role("R1", "Role 1");
        Role r2 = new Role("R2", "Role 2");
        roleManager.add(r1);
        roleManager.add(r2);

        Permission p1 = new Permission("READ", "docs", "Read");
        Permission p2 = new Permission("WRITE", "code", "Write");

        r1.addPermission(p1);
        r2.addPermission(p2);

        AssignmentMetadata meta = AssignmentMetadata.now("admin", null);
        manager.add(new PermanentAssignment(u, r1, meta));
        manager.add(new PermanentAssignment(u, r2, meta));

        Set<Permission> perms = manager.getUserPermissions(u);
        assertEquals(2, perms.size());
        assertTrue(perms.stream().anyMatch(p -> p.name().equals("READ")));
        assertTrue(perms.stream().anyMatch(p -> p.name().equals("WRITE")));
    }

    @Test
    void finalizeExpiredTemporaryAssignments_countsOnlyDue() {
        User u = User.create("due", "Due", "due@ex.com");
        Role r = new Role("RT", "Role T");
        userManager.add(u);
        roleManager.add(r);

        AssignmentMetadata meta = AssignmentMetadata.now("admin", "x");
        TemporaryAssignment past = new TemporaryAssignment(u, r, meta, "2000-06-01 00:00", false);
        manager.add(past);

        assertEquals(1, manager.finalizeExpiredTemporaryAssignments());
        assertEquals(0, manager.finalizeExpiredTemporaryAssignments());
    }
}