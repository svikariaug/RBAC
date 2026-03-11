import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoleManagerTest {

    private RoleManager manager;
    private AssignmentManager assignmentManager; // заглушка

    @BeforeEach
    void setUp() {
        manager = new RoleManager();
        assignmentManager = new AssignmentManager(new UserManager(), manager);
        manager.setAssignmentManager(assignmentManager);
    }

    @Test
    void add_duplicateName_shouldThrow() {
        Role r1 = new Role("Admin", "Full access");
        Role r2 = new Role("Admin", "Duplicate");

        manager.add(r1);
        assertThrows(IllegalArgumentException.class, () -> manager.add(r2));
    }

    @Test
    void addPermissionToRole_shouldAddPermission() {
        Role role = new Role("Editor", "Can edit");
        manager.add(role);

        Permission p = new Permission("WRITE", "articles", "Write articles");
        manager.addPermissionToRole("Editor", p);

        Optional<Role> found = manager.findByName("Editor");
        assertTrue(found.isPresent());
        assertTrue(found.get().hasPermission("WRITE", "articles"));
    }

    @Test
    void removeRole_withActiveAssignments_shouldThrow() {
        User u = User.create("usr", "User", "u@ex.com");
        Role r = new Role("Tester", "Test role");
        manager.add(r);

        assertTrue(manager.remove(r));
    }
}