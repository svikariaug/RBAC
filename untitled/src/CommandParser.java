import java.util.*;

public class CommandParser {
    private final Map<String, Command> commands;
    private final Map<String, String> commandDescriptions;

    public CommandParser() {
        this.commands = new HashMap<>();
        this.commandDescriptions = new HashMap<>();
    }

    public void registerCommand(String name, String description, Command command) {
        String lowerCaseName = name.toLowerCase();
        commands.put(lowerCaseName, command);
        commandDescriptions.put(lowerCaseName, description);
    }

    public void executeCommand(String commandName, Scanner scanner, RBACSystem system) {
        String lowerCaseName = commandName.toLowerCase();
        Command command = commands.get(lowerCaseName);

        if (command != null) {
            try {
                command.execute(scanner, system);
            } catch (Exception e) {
                System.out.println("Ошибка: " + e.getMessage());
            }
        } else {
            System.out.println("Неизвестная команда: " + commandName);
        }
    }

    public void printHelp() {
        System.out.println("\nДоступные команды:");
        List<String> sortedCommands = new ArrayList<>(commands.keySet());
        Collections.sort(sortedCommands);

        for (String cmd : sortedCommands) {
            String description = commandDescriptions.get(cmd);
            System.out.println("  " + cmd + " - " + description);
        }
    }

    public void parseAndExecute(String input, Scanner scanner, RBACSystem system) {
        if (input == null || input.trim().isEmpty()) {
            return;
        }

        String[] parts = input.trim().split("\\s+", 2);
        String commandName = parts[0].toLowerCase();

        if (parts.length > 1) {
            // Если есть аргументы, создаём новый Scanner из них
            Scanner commandScanner = new Scanner(parts[1]);
            executeCommand(commandName, commandScanner, system);
        } else {
            // Если аргументов нет, но scanner не null, передаём его
            if (scanner != null) {
                executeCommand(commandName, scanner, system);
            } else {
                // Если scanner null, создаём пустой
                executeCommand(commandName, new Scanner(""), system);
            }
        }
    }
}