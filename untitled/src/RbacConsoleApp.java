import java.util.Scanner;

/**
 * Интерактивная консольная утилита RBAC.
 * <p>
 * Запуск: {@code java Main [--tick N]} — период планировщика N секунд (0 = без фона).
 */
public final class RbacConsoleApp {

    private RbacConsoleApp() {}

    public static void main(String[] args) {
        int tick = RBACSystem.getDefaultSchedulerPeriodSeconds();
        for (int i = 0; i < args.length; i++) {
            if (("--tick".equals(args[i]) || "-n".equals(args[i])) && i + 1 < args.length) {
                tick = Integer.parseInt(args[++i]);
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                printUsage();
                return;
            }
        }

        RBACSystem system = new RBACSystem(tick);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                system.shutdownAsyncServices();
            } catch (Throwable ignored) {
                // graceful best-effort
            }
        }));

        system.initialize();

        CommandParser parser = new CommandParser();
        CommandRegistry.registerAllCommands(parser);

        String schedHint = tick <= 0 ? "выключен (schedule-interval или --tick для включения)"
                : "раз в " + tick + " с (schedule-interval чтобы сменить N)";
        System.out.println("RBAC — пользователи, роли, назначения. Команда help — справка.");
        System.out.println("Планировщик истечения временных ролей: " + schedHint);

        Scanner lineScanner = new Scanner(System.in);
        while (lineScanner.hasNextLine()) {
            String line = lineScanner.nextLine();
            try {
                parser.parseAndExecute(line, lineScanner, system);
            } catch (Throwable t) {
                System.out.println("Ошибка: " + t.getMessage());
            }
        }
        system.shutdownAsyncServices();
        lineScanner.close();
    }

    private static void printUsage() {
        System.out.println("Использование: java Main [опции]");
        System.out.println("  --tick, -n N   период ScheduledExecutorService, секунды (N=0 — без расписания)");
        System.out.println("  --help, -h     эта справка");
    }
}
