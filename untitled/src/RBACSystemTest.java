import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;

class RBACSystemTest {
    private RBACSystem system;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void setUp() {
        system = new RBACSystem(0);
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        if (system != null) {
            system.shutdownAsyncServices();
        }
    }

    @Test
    @DisplayName("Конструктор должен создавать систему с менеджерами")
    void testConstructor() {
        assertNotNull(system.getUserManager());
        assertNotNull(system.getRoleManager());
        assertNotNull(system.getAssignmentManager());
        assertEquals("system", system.getCurrentUser());
    }

    @Test
    @DisplayName("setCurrentUser должен изменять текущего пользователя")
    void testSetCurrentUser() {
        system.initialize();
        system.setCurrentUser("admin");
        assertEquals("admin", system.getCurrentUser());
    }

    @Test
    @DisplayName("setCurrentUser с несуществующим пользователем должен выбрасывать исключение")
    void testSetCurrentUserInvalid() {
        system.initialize();
        assertThrows(IllegalArgumentException.class, () -> {
            system.setCurrentUser("nonexistent");
        });
    }

    @Test
    @DisplayName("setCurrentUser с system должен работать")
    void testSetCurrentUserSystem() {
        system.setCurrentUser("system");
        assertEquals("system", system.getCurrentUser());

        system.setCurrentUser(null);
        assertEquals("system", system.getCurrentUser());
    }

    @Test
    @DisplayName("initialize должен создавать начальные данные")
    void testInitialize() {
        assertEquals(0, system.getUserManager().count());
        assertEquals(0, system.getRoleManager().count());

        system.initialize();

        assertTrue(system.getUserManager().count() > 0);
        assertTrue(system.getRoleManager().count() > 0);
        assertTrue(system.getAssignmentManager().count() > 0);
    }

    @Test
    @DisplayName("generateStatistics должен возвращать статистику")
    void testGenerateStatistics() {
        system.initialize();
        String stats = system.generateStatistics();

        assertTrue(stats.contains("=== Статистика RBAC ==="));
        assertTrue(stats.contains("Текущий пользователь:"));
        assertTrue(stats.contains("Пользователей:"));
        assertTrue(stats.contains("Ролей:"));
        assertTrue(stats.contains("Назначений:"));
    }

    @Test
    @DisplayName("getUserManager должен возвращать UserManager")
    void testGetUserManager() {
        assertNotNull(system.getUserManager());
    }

    @Test
    @DisplayName("getRoleManager должен возвращать RoleManager")
    void testGetRoleManager() {
        assertNotNull(system.getRoleManager());
    }

    @Test
    @DisplayName("getAssignmentManager должен возвращать AssignmentManager")
    void testGetAssignmentManager() {
        assertNotNull(system.getAssignmentManager());
    }

    @Test
    @DisplayName("Повторный вызов initialize не должен ломать систему")
    void testDoubleInitialize() {
        system.initialize();
        int userCount = system.getUserManager().count();
        int roleCount = system.getRoleManager().count();
        int assignCount = system.getAssignmentManager().count();

        try {
            system.initialize();
            assertEquals(userCount, system.getUserManager().count());
            assertEquals(roleCount, system.getRoleManager().count());
            assertEquals(assignCount, system.getAssignmentManager().count());
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test
    @DisplayName("Создание системы с пользователем после инициализации")
    void testCreateSystemWithUserAfterInit() {
        system.initialize();

        RBACSystem newSystem = new RBACSystem(0);

        system.getUserManager().findByUsername("admin").ifPresent(admin -> {
            newSystem.getUserManager().add(admin);
        });

        newSystem.setCurrentUser("admin");
        assertEquals("admin", newSystem.getCurrentUser());
        newSystem.shutdownAsyncServices();
    }
}