import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public final class BackgroundExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public BackgroundExecutor(int poolSize) {
        int n = Math.max(1, poolSize);
        ThreadFactory factory = new NamedDaemonThreadFactory("rbac-worker");
        this.executor = Executors.newFixedThreadPool(n, factory);
    }

    public BackgroundExecutor() {
        this(Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors())));
    }

    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
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
