package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the three properties of the reactor executor that the async design leans on, each of which is
 * silently wrong under a plausible alternative wiring — see {@code FamilyEmailNotificationsPlan.md}
 * §4.1. The bean is constructed directly rather than through a Spring context, because what is under
 * test is the executor's own configuration.
 */
class EventReactorExecutorTest {

    private final ExecutorService executor = new EventSourcingConfig().eventReactorExecutor();

    /**
     * The behavioural case below shows what {@code shutdownNow()} does; this one shows that Spring
     * will be the one calling it. They are separate because the behavioural test calls the method
     * itself, so it stays green if the attribute is deleted — and deleting it does not fall back to
     * "no shutdown", it falls back to the inferred {@code shutdown()}, which drains the queue. That
     * is the failure this pins, and it would otherwise be a rule stated only in a javadoc.
     */
    @Test
    void theBeanDeclaresShutdownNowRatherThanLettingSpringInferShutdown() throws NoSuchMethodException {
        Bean bean = EventSourcingConfig.class
                .getMethod("eventReactorExecutor")
                .getAnnotation(Bean.class);

        assertThat(bean.destroyMethod())
                .as("Spring infers shutdown() for an ExecutorService bean, and shutdown() drains the "
                    + "queue: every queued reactor task would run against a closing datasource")
                .isEqualTo("shutdownNow");
    }

    @Test
    void aQueuedTaskIsDroppedAtShutdownRatherThanDrained() throws InterruptedException {
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);
        AtomicInteger queuedTaskRuns = new AtomicInteger();
        executor.execute(() -> {
            firstTaskStarted.countDown();
            awaitQuietly(releaseFirstTask);
        });
        assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS))
                .as("the single worker picked up the first task")
                .isTrue();
        executor.execute(queuedTaskRuns::incrementAndGet);

        executor.shutdownNow();
        releaseFirstTask.countDown();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS))
                .as("the executor terminated")
                .isTrue();

        assertThat(queuedTaskRuns.get())
                .as("a task still queued at shutdown is dropped, not drained — Spring's inferred "
                    + "shutdown() would run it, POSTing against a closing datasource")
                .isZero();
    }

    @Test
    void aFullQueueRejectsRatherThanRunningTheTaskOnTheCaller() throws InterruptedException {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        String callerThread = Thread.currentThread().getName();
        AtomicInteger runsOnCaller = new AtomicInteger();
        executor.execute(() -> awaitQuietly(releaseWorker));
        fillTheQueue();

        try {
            assertThatThrownBy(() -> executor.execute(() -> {
                if (Thread.currentThread().getName().equals(callerThread)) {
                    runsOnCaller.incrementAndGet();
                }
            }))
                    .as("AbortPolicy, never CallerRunsPolicy: a reactor run on the append thread "
                        + "would append inside another append's critical section")
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(runsOnCaller.get())
                    .isZero();
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * Asserts on thread <em>identity</em>, not on the thread's name: every thread this factory makes
     * is named {@code event-reactor}, so a name-based assertion passes against a two-thread pool —
     * which is how it was first written here, and it passed the mutation it was meant to catch.
     */
    @Test
    void everyTaskRunsOnTheSameSingleThread() throws InterruptedException {
        int taskCount = 20;
        CountDownLatch allDone = new CountDownLatch(taskCount);
        Set<Long> threadIds = ConcurrentHashMap.newKeySet();
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                threadIds.add(Thread.currentThread().threadId());
                threadNames.add(Thread.currentThread().getName());
                // Hold the worker briefly, so a second worker in a wider pool has work to pick up
                // rather than finding the queue already drained by the first.
                awaitQuietly(new CountDownLatch(1), 5);
                allDone.countDown();
            });
        }

        assertThat(allDone.await(5, TimeUnit.SECONDS))
                .as("every task ran")
                .isTrue();
        executor.shutdownNow();

        assertThat(threadIds)
                .as("one thread is a correctness property: it is what makes events reach every "
                    + "reactor in log order and keeps two triggers for one subject from racing")
                .hasSize(1);
        assertThat(threadNames)
                .as("and it is identifiable in a thread dump")
                .containsExactly("event-reactor");
    }

    private void fillTheQueue() {
        // The worker is blocked, so every accepted task lands in the queue; stop as soon as one is
        // refused, which is the queue's real capacity rather than a number repeated from the config.
        for (int i = 0; i < 1_000; i++) {
            try {
                executor.execute(() -> { });
            } catch (RejectedExecutionException _) {
                return;
            }
        }
        throw new IllegalStateException("the reactor queue accepted 1000 tasks — it is not bounded");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        awaitQuietly(latch, 5_000);
    }

    private static void awaitQuietly(CountDownLatch latch, long timeoutMillis) {
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
