import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class WorkersLoadStressTest {

    private RBACSystem system;

    @BeforeEach
    void setUp() {
        system = new RBACSystem(0);
        system.initialize();
    }

    @AfterEach
    void tearDown() {
        system.shutdownAsyncServices();
    }

    @Test
    @DisplayName("Параллельные создания/назначения и поиски без дубликатов и пропусков")
    void concurrentUserCreateAssignAndSearch() throws Exception {
        Role viewer = system.getRoleManager().findByName("VIEWER").orElseThrow();

        int threads = 8;
        int opsPerThread = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                UserManager um = system.getUserManager();
                AssignmentManager am = system.getAssignmentManager();
                for (int i = 0; i < opsPerThread; i++) {
                    String username = "load_t" + tid + "_u" + i;
                    String email = "load" + tid + "_" + i + "@stress.test";

                    try {
                        User u = User.create(username, "Load " + tid + "/" + i, email);
                        um.add(u);
                        String actor = system.getCurrentUser() != null ? system.getCurrentUser() : "system";
                        am.add(new PermanentAssignment(u, viewer,
                                AssignmentMetadata.now(actor, "stress")));
                    } catch (IllegalArgumentException ignored) {
                        
                    }

                    um.findByFilter(UserFilters.byUsernameContains("load_t"));
                    um.findByFilter(UserFilters.byEmailDomain("@stress.test"));
                    system.getAssignmentManager().findByFilter(AssignmentFilters.byUsername(username));
                }
            }));
        }

        assertTrue(ready.await(60, TimeUnit.SECONDS), "потоки не стартовали");
        start.countDown();

        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        List<String> stressUsernames = system.getUserManager().findAll().stream()
                .map(User::username)
                .filter(u -> u.startsWith("load_t"))
                .sorted()
                .toList();

        assertEquals(threads * opsPerThread, stressUsernames.size());

        Set<String> unique = stressUsernames.stream().collect(Collectors.toSet());
        assertEquals(stressUsernames.size(), unique.size(), "не должно быть дубликатов username в хранилище");
    }
}
