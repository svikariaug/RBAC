import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Периодическое обслуживание: истёкшие временные назначения и отчёт статистики в audit log.
 */
public final class ScheduledMaintenanceTask implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final RBACSystem system;

    public ScheduledMaintenanceTask(RBACSystem system, long period, TimeUnit unit) {
        this.system = system;
        ThreadFactory factory = new NamedDaemonThreadFactory("rbac-schedule");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
        this.scheduler.scheduleAtFixedRate(this::safeTick, 0, period, unit);
    }

    /**
     * Один цикл обслуживания (удобно для тестов без планировщика).
     */
    public static void runMaintenanceTick(RBACSystem system) {
        int closed = system.getAssignmentManager().finalizeExpiredTemporaryAssignments();
        String compact = compactStats(system);
        system.getAuditLog().log("SCHEDULER_TICK", "scheduler", "rbac",
                "closedTemp=" + closed + "; " + compact);
    }

    private static String compactStats(RBACSystem s) {
        var am = s.getAssignmentManager();
        return String.format("users=%d roles=%d assign=%d active=%d inactive=%d",
                s.getUserManager().count(),
                s.getRoleManager().count(),
                am.count(),
                am.getActiveAssignments().size(),
                am.getExpiredAssignments().size());
    }

    private void safeTick() {
        try {
            runMaintenanceTick(system);
        } catch (Throwable t) {
            system.getAuditLog().log("SCHEDULER_FAIL", "scheduler", "rbac",
                    t.getMessage() != null ? t.getMessage() : "error");
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger(1);
        private final String prefix;

        NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
