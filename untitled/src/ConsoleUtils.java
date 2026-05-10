import java.util.List;
import java.util.Scanner;

public final class ConsoleUtils {
    private ConsoleUtils() {}

    public static String promptString(Scanner scanner, String message, boolean required) {
        while (true) {
            System.out.print(message);
            String value = scanner.nextLine();
            value = ValidationUtils.normalizeString(value);
            if (!required) return value;
            if (value != null && !value.isBlank()) return value;
            System.out.println("Значение обязательно. Повторите ввод.");
        }
    }

    public static int promptInt(Scanner scanner, String message, int min, int max) {
        if (min > max) throw new IllegalArgumentException("min must be <= max");
        while (true) {
            System.out.print(message);
            String raw = scanner.nextLine().trim();
            try {
                int v = Integer.parseInt(raw);
                if (v < min || v > max) {
                    System.out.println("Число должно быть в диапазоне " + min + "..." + max);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Неверный формат числа. Повторите ввод.");
            }
        }
    }

    public static boolean promptYesNo(Scanner scanner, String message) {
        while (true) {
            System.out.print(message);
            String v = scanner.nextLine().trim().toLowerCase();
            if (v.equals("да") || v.equals("y") || v.equals("yes")) return true;
            if (v.equals("нет") || v.equals("n") || v.equals("no")) return false;
            System.out.println("Введите 'да' или 'нет'.");
        }
    }

    public static <T> T promptChoice(Scanner scanner, String message, List<T> options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Options cannot be null/empty");
        }

        while (true) {
            System.out.println(message);
            for (int i = 0; i < options.size(); i++) {
                System.out.printf("%d. %s%n", i + 1, String.valueOf(options.get(i)));
            }
            int choice = promptInt(scanner, "Ваш выбор: ", 1, options.size());
            return options.get(choice - 1);
        }
    }
}

