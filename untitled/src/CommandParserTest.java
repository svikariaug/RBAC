import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;

class CommandParserTest {
    private CommandParser parser;
    private RBACSystem system;
    private ByteArrayOutputStream outContent;

    @BeforeEach
    void setUp() {
        parser = new CommandParser();
        system = new RBACSystem();
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    @DisplayName("Регистрация команды должна добавлять её в парсер")
    void testRegisterCommand() {
        Command testCommand = (scanner, sys) -> System.out.println("Test command executed");
        parser.registerCommand("test", "тестовая команда", testCommand);

        parser.executeCommand("test", new Scanner(""), system);
        assertTrue(outContent.toString().contains("Test command executed"));
    }

    @Test
    @DisplayName("Выполнение существующей команды должно работать")
    void testExecuteExistingCommand() {
        Command testCommand = (scanner, sys) -> System.out.println("Test OK");
        parser.registerCommand("test", "тест", testCommand);

        parser.executeCommand("test", new Scanner(""), system);
        assertTrue(outContent.toString().contains("Test OK"));
    }

    @Test
    @DisplayName("Выполнение несуществующей команды должно выводить сообщение об ошибке")
    void testExecuteNonExistingCommand() {
        parser.executeCommand("unknown", new Scanner(""), system);
        assertTrue(outContent.toString().contains("Неизвестная команда: unknown"));
    }

    @Test
    @DisplayName("Парсинг и выполнение команды с аргументами должен работать")
    void testParseAndExecuteWithArgs() {
        Command testCommand = (scanner, sys) -> {
            String arg = scanner.nextLine();
            System.out.println("Received: " + arg);
        };
        parser.registerCommand("test", "тест", testCommand);

        String input = "test argument";

        parser.parseAndExecute(input, null, system);

        String output = outContent.toString();
        assertTrue(output.contains("Received: argument"));
    }

    @Test
    @DisplayName("Пустой ввод должен игнорироваться")
    void testEmptyInput() {
        parser.parseAndExecute("", new Scanner(""), system);
        parser.parseAndExecute("   ", new Scanner(""), system);
        parser.parseAndExecute(null, new Scanner(""), system);

        assertEquals("", outContent.toString());
    }

    @Test
    @DisplayName("printHelp должен выводить список всех команд")
    void testPrintHelp() {
        parser.registerCommand("cmd1", "описание 1", (s, sys) -> {});
        parser.registerCommand("cmd2", "описание 2", (s, sys) -> {});

        parser.printHelp();
        String output = outContent.toString();
        assertTrue(output.contains("cmd1"));
        assertTrue(output.contains("cmd2"));
        assertTrue(output.contains("описание 1"));
        assertTrue(output.contains("описание 2"));
    }

    @Test
    @DisplayName("Команда с ошибкой выполнения должна выводить сообщение об ошибке")
    void testCommandWithException() {
        Command failingCommand = (scanner, sys) -> {
            throw new RuntimeException("Тестовая ошибка");
        };
        parser.registerCommand("fail", "команда с ошибкой", failingCommand);

        parser.executeCommand("fail", new Scanner(""), system);
        assertTrue(outContent.toString().contains("Ошибка: Тестовая ошибка"));
    }

    @Test
    @DisplayName("Команда с аргументами в несколько строк должна работать")
    void testCommandWithMultipleLines() {
        Command testCommand = (scanner, sys) -> {
            String firstLine = scanner.nextLine();
            String secondLine = scanner.nextLine();
            System.out.println("First: " + firstLine + ", Second: " + secondLine);
        };
        parser.registerCommand("multiline", "тест с несколькими строками", testCommand);

        String input = "multiline\narg1\narg2";

        parser.parseAndExecute(input, null, system);

        String output = outContent.toString();
        assertTrue(output.contains("First: arg1, Second: arg2"));
    }
}