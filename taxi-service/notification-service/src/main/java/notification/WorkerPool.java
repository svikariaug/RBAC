package notification;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final NotificationService notificationService;
    private final int poolSize;
    private final long sendDelayMs;

    private ExecutorService executor;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public WorkerPool(
            NotificationService notificationService,
            @Value("${taxi.notification.workers:4}") int poolSize,
            @Value("${taxi.notification.send-delay-ms:300}") long sendDelayMs) {
        this.notificationService = notificationService;
        this.poolSize = Math.max(3, Math.min(5, poolSize));
        this.sendDelayMs = sendDelayMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        log.info("Starting {} notification workers", poolSize);
        for (int i = 0; i < poolSize; i++) {
            int idx = i;
            executor.submit(() -> runLoop(idx));
        }
    }

    private void runLoop(int workerIndex) {
        while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
            try {
                var claimed = notificationService.claimNext();
                if (claimed.isEmpty()) {
                    Thread.sleep(150);
                    continue;
                }
                ClaimedRow task = claimed.get();
                try {
                    notificationService.simulateSend(task, sendDelayMs);
                    notificationService.markSent(task.id());
                } catch (Exception ex) {
                    log.warn("Worker {} failed task {}: {}", workerIndex, task.id(), ex.getMessage());
                    notificationService.markFailure(task.id());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker {} loop error", workerIndex, e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("Notification worker {} stopped", workerIndex);
    }

    @PreDestroy
    public void shutdownGracefully() {
        log.info("Graceful shutdown: stopping notification workers");
        stopped.set(true);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(25, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
