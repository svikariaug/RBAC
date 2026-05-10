import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RbacSnapshotIOTest {

    private RBACSystem system;

    @BeforeEach
    void setUp() {
        system = new RBACSystem(0);
        system.initialize();
    }

    @AfterEach
    void tearDown() {
        system.shutdownAsyncServices();
    }

    @Test
    void exportImportRoundtripPreservesCounts() throws Exception {
        int users = system.getUserManager().count();
        int roles = system.getRoleManager().count();
        int assigns = system.getAssignmentManager().count();

        Path tmp = Files.createTempFile("rbac-io", ".snap");
        Files.deleteIfExists(tmp);
        String path = tmp.toString();

        RbacSnapshotIO.exportToFile(system, path);
        system.clearAllData();
        assertEquals(0, system.getUserManager().count());

        RbacSnapshotIO.importFromFile(system, path);

        assertEquals(users, system.getUserManager().count());
        assertEquals(roles, system.getRoleManager().count());
        assertEquals(assigns, system.getAssignmentManager().count());

        Files.deleteIfExists(tmp);
    }
}
