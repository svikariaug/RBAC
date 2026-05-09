import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class AuditLog {
    private final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());

    private final BlockingQueue<AuditEntry> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong enqueued = new AtomicLong(0);
    private final AtomicLong processed = new AtomicLong(0);
    private final Thread worker;

    public record AuditEntry(
            String timestamp,
            String action,
            String performer,
            String target,
            String details
    ) {}

    public AuditLog() {
        this.worker = new Thread(this::runWorker, "audit-log-worker");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public void log(String action, String performer, String target, String details) {
        AuditEntry entry = new AuditEntry(
                DateUtils.getCurrentDateTime(),
                ValidationUtils.normalizeString(action),
                ValidationUtils.normalizeString(performer),
                ValidationUtils.normalizeString(target),
                ValidationUtils.normalizeString(details)
        );

        if (!running.get()) {
            entries.add(entry);
            return;
        }

        enqueued.incrementAndGet();
        queue.offer(entry);
    }

    public List<AuditEntry> getAll() {
        flush(2, TimeUnit.SECONDS);
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public List<AuditEntry> getByPerformer(String performer) {
        flush(2, TimeUnit.SECONDS);
        String p = ValidationUtils.normalizeString(performer);
        synchronized (entries) {
            return entries.stream()
                    .filter(e -> e.performer() != null && e.performer().equalsIgnoreCase(p))
                    .collect(Collectors.toList());
        }
    }

    public List<AuditEntry> getByAction(String action) {
        flush(2, TimeUnit.SECONDS);
        String a = ValidationUtils.normalizeString(action);
        synchronized (entries) {
            return entries.stream()
                    .filter(e -> e.action() != null && e.action().equalsIgnoreCase(a))
                    .collect(Collectors.toList());
        }
    }

    public void printLog() {
        flush(2, TimeUnit.SECONDS);
        if (entries.isEmpty()) {
            System.out.println("Audit log пуст.");
            return;
        }

        List<String[]> rows = new ArrayList<>();
        synchronized (entries) {
            for (AuditEntry e : entries) {
                rows.add(new String[]{
                        FormatUtils.truncate(e.timestamp(), 19),
                        FormatUtils.truncate(e.action(), 16),
                        FormatUtils.truncate(e.performer(), 16),
                        FormatUtils.truncate(e.target(), 18),
                        FormatUtils.truncate(e.details(), 40)
                });
            }
        }
        String table = FormatUtils.formatTable(
                new String[]{"Timestamp", "Action", "Performer", "Target", "Details"},
                rows
        );
        System.out.println(table);
    }

    public void saveToFile(String filename) {
        flush(2, TimeUnit.SECONDS);
        ValidationUtils.requireNonEmpty(filename, "filename");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("timestamp\taction\tperformer\ttarget\tdetails\n");
            synchronized (entries) {
                for (AuditEntry e : entries) {
                    writer.write(safeTsv(e.timestamp())); writer.write("\t");
                    writer.write(safeTsv(e.action())); writer.write("\t");
                    writer.write(safeTsv(e.performer())); writer.write("\t");
                    writer.write(safeTsv(e.target())); writer.write("\t");
                    writer.write(safeTsv(e.details()));
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить audit log: " + e.getMessage(), e);
        }
    }

    public boolean flush(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        long target = enqueued.get();
        while (System.nanoTime() < deadline) {
            if (processed.get() >= target && queue.isEmpty()) return true;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return processed.get() >= target;
    }

    public void shutdownAndAwait(long timeout, TimeUnit unit) {
        running.set(false);
        worker.interrupt();
        try {
            worker.join(unit.toMillis(timeout));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        flush(timeout, unit);
    }

    private void runWorker() {
        while (running.get() || !queue.isEmpty()) {
            try {
                AuditEntry e = queue.poll(200, TimeUnit.MILLISECONDS);
                if (e == null) continue;
                entries.add(e);
                processed.incrementAndGet();
            } catch (InterruptedException ignored) {
                
            } catch (Throwable ignored) {
                
            }
        }
    }

    private static String safeTsv(String s) {
        if (s == null) return "";
        return s.replace("\t", " ").replace("\n", "\\n").replace("\r", "\\r");
    }
}

