import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MultiThreadCalcSimulation {

    private static final int THREADS = 5;
    private static final int BAR_LENGTH = 30;
    private static final int MIN_STEP_DELAY_MS = 40;
    private static final int MAX_STEP_DELAY_MS = 120;

    private static final Object CONSOLE_LOCK = new Object();

    private static final String ESC = "\033[";
    private static final String CLEAR_SCREEN = ESC + "2J";
    private static final String HIDE_CURSOR = ESC + "?25l";
    private static final String SHOW_CURSOR = ESC + "?25h";

    public static void main(String[] args) throws InterruptedException {
        int threads = THREADS;
        int barLength = BAR_LENGTH;

        synchronized (CONSOLE_LOCK) {
            System.out.print(CLEAR_SCREEN);
            moveCursor(1, 1);
            System.out.println("Имитация многопоточного расчёта");
            System.out.println("Поток | TID                 | Progress");
            System.out.println("-".repeat(70));
            System.out.print(HIDE_CURSOR);

            for (int i = 0; i < threads; i++) {
                // строки прогресса начнутся с 4-й строки (1-based)
                int row = progressRow(i);
                moveCursor(row, 1);
                System.out.print(formatLine(i + 1, 0, barLength, "running", null));
            }
            System.out.flush();
        }

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Thread t = new Thread(() -> runWorker(idx, barLength));
            t.setName("calc-" + (idx + 1));
            workers.add(t);
        }

        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        synchronized (CONSOLE_LOCK) {
            moveCursor(progressRow(threads) + 2, 1);
            System.out.print(SHOW_CURSOR);
            System.out.println("Готово.");
            System.out.flush();
        }
    }

    private static void runWorker(int idx, int barLength) {
        long startNs = System.nanoTime();
        long tid = Thread.currentThread().threadId();

        for (int filled = 0; filled <= barLength; filled++) {
            // имитируем «расчёт»
            sleepRandom(MIN_STEP_DELAY_MS, MAX_STEP_DELAY_MS);

            synchronized (CONSOLE_LOCK) {
                moveCursor(progressRow(idx), 1);
                System.out.print(formatLine(idx + 1, tid, filled, barLength, "running", null));
                System.out.flush();
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        synchronized (CONSOLE_LOCK) {
            moveCursor(progressRow(idx), 1);
            System.out.print(formatLine(idx + 1, tid, barLength, barLength, "done", elapsedMs));
            System.out.flush();
        }
    }

    private static int progressRow(int idx) {
        return 4 + idx;
    }

    private static String formatLine(int number, long tid, int filled, int barLength, String status, Long elapsedMs) {
        String bar = progressBar(filled, barLength);
        String tidStr = tid == 0 ? "-" : String.valueOf(tid);

        String suffix;
        if ("done".equals(status) && elapsedMs != null) {
            suffix = String.format("  time=%dms", elapsedMs);
        } else {
            suffix = "";
        }

        // Чтобы строка полностью перерисовывалась, добиваем пробелами до фиксированной ширины
        String base = String.format("%-5d | %-19s | %s%s", number, tidStr, bar, suffix);
        return padRight(base, 70);
    }

    private static String formatLine(int number, int filled, int barLength, String status, Long elapsedMs) {
        return formatLine(number, 0L, filled, barLength, status, elapsedMs);
    }

    private static String progressBar(int filled, int total) {
        int safeFilled = Math.max(0, Math.min(filled, total));
        String body = "#".repeat(safeFilled) + ".".repeat(total - safeFilled);
        int percent = (int) Math.round((safeFilled * 100.0) / total);
        return "[" + body + "] " + String.format("%3d%%", percent);
    }

    private static void moveCursor(int row, int col) {
        System.out.print(ESC + row + ";" + col + "H");
    }

    private static void sleepRandom(int minMs, int maxMs) {
        int delay = ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String padRight(String s, int length) {
        if (s.length() >= length) return s;
        return s + " ".repeat(length - s.length());
    }
}

