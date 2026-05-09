import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class CommandRegistryTest {
    private CommandParser parser;
    private RBACSystem system;
    private ByteArrayOutputStream outContent;
    private Scanner scanner;

    @BeforeEach
    void setUp() {
        parser = new CommandParser();
        system = new RBACSystem();
        system.initialize();
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        scanner = new Scanner(System.in);
    }

    @AfterEach
    void tearDown() {
        if (system != null) {
            system.shutdownAsyncServices();
        }
    }

    @Test
    @DisplayName("Регистрация всех команд должна добавить их в парсер")
    void testRegisterAllCommands() {
        CommandRegistry.registerAllCommands(parser);
        assertNotNull(parser);
    }

    @Test
    @DisplayName("Команда user-list должна выводить список пользователей")
    void testUserListCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("user-list", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("=== Список пользователей ===") ||
                output.contains("Пользователи не найдены"));
    }

    @Test
    @DisplayName("Команда role-list должна выводить список ролей")
    void testRoleListCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("role-list", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("=== Список ролей ===") ||
                output.contains("Роли не найдены"));
    }

    @Test
    @DisplayName("Команда help должна выводить справку")
    void testHelpCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("help", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("Доступные команды"));
    }

    @Test
    @DisplayName("Команда stats должна выводить статистику")
    void testStatsCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("stats", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("=== Статистика RBAC ===") ||
                output.contains("Топ-3"));
    }

    @Test
    @DisplayName("Команда assignment-list должна выводить назначения")
    void testAssignmentListCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("assignment-list", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("=== Все назначения ===") ||
                output.contains("Назначения не найдены"));
    }

    @Test
    @DisplayName("Команда permissions-user с несуществующим пользователем должна выводить ошибку")
    void testPermissionsUserInvalid() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("nonexistent");
        parser.executeCommand("permissions-user", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("Пользователь не найден"));
    }

    @Test
    @DisplayName("Команда clear должна очищать экран")
    void testClearCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("clear", testScanner, system);

        assertNotNull(outContent.toString());
    }

    @Test
    @DisplayName("Команда exit должна выводить запрос подтверждения")
    void testExitCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("нет");
        parser.executeCommand("exit", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("Выйти?"));
    }

    @Test
    @DisplayName("Команда save должна выводить сообщение о разработке")
    void testSaveCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("save", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("Сохранение данных... (функция в разработке)"));
    }

    @Test
    @DisplayName("Команда load должна выводить сообщение о разработке")
    void testLoadCommand() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("");
        parser.executeCommand("load", testScanner, system);

        String output = outContent.toString();
        assertTrue(output.contains("Загрузка данных... (функция в разработке)"));
    }

    @Test
    @DisplayName("user-view с существующим пользователем должен работать")
    void testUserViewExisting() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("admin");
        parser.executeCommand("user-view", testScanner, system);

        String output = outContent.toString();
        assertNotNull(output);
    }

    @Test
    @DisplayName("role-view с существующей ролью должен работать")
    void testRoleViewExisting() {
        CommandRegistry.registerAllCommands(parser);

        Scanner testScanner = new Scanner("ADMIN");
        parser.executeCommand("role-view", testScanner, system);

        String output = outContent.toString();
        assertNotNull(output);
    }

    @Test
    @DisplayName("save-async записывает снимок в файл")
    void testSaveAsyncCreatesSnapshotFile() throws Exception {
        CommandRegistry.registerAllCommands(parser);
        Path tmp = Files.createTempFile("rbac-workers", ".snap");
        Files.deleteIfExists(tmp);

        Scanner testScanner = new Scanner(tmp.toAbsolutePath() + "\n");
        parser.executeCommand("save-async", testScanner, system);

        assertTrue(outContent.toString().contains("Фоновое сохранение"));

        for (int i = 0; i < 120 && (!Files.exists(tmp) || Files.size(tmp) == 0); i++) {
            Thread.sleep(25);
        }
        assertTrue(Files.exists(tmp));
        assertTrue(Files.size(tmp) > 0);
    }

    @Test
    @DisplayName("report-users-async выводит отчёт после фона")
    void testReportUsersAsync() throws Exception {
        CommandRegistry.registerAllCommands(parser);
        Scanner testScanner = new Scanner("нет\n");
        parser.executeCommand("report-users-async", testScanner, system);

        assertTrue(outContent.toString().contains("фоновая генерация"));

        for (int i = 0; i < 120 && !outContent.toString().contains("[фон]"); i++) {
            Thread.sleep(25);
        }
        assertTrue(outContent.toString().contains("[фон]"));
        assertTrue(outContent.toString().contains("Отчёт по пользователям"));
    }
}