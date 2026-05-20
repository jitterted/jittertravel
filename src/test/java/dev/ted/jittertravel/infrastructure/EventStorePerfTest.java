package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@Tag("performance")
class EventStorePerfTest extends AbstractTestcontainerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(EventStorePerfTest.class);

    @Autowired
    private PostgresPersister realPersister;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void benchmarkSynchronousDatabaseWrites() {
        try {
            var eventStore = new EventStore(meterRegistry, realPersister);
            int warmupIterations = 100;
            int measuredIterations = 1_000;

            log.info("Starting Database Warmup ({} writes)...", warmupIterations);
            for (int i = 0; i < warmupIterations; i++) {
                UUID commandId = UUID.randomUUID();
                PlanTentativeConferenceRequest request = createCommandRequest(commandId);
                transactionTemplate.executeWithoutResult(_ -> {
                    realPersister.saveCommand(commandId, request);
                    eventStore.append(Stream.of(createSampleEvent()), commandId);
                });
            }

            log.info("Starting Measured Synchronous Writes ({} writes)...", measuredIterations);
            List<Long> latenciesNanos = new ArrayList<>(measuredIterations);

            long totalStart = System.nanoTime();

            for (int i = 0; i < measuredIterations; i++) {
                UUID commandId = UUID.randomUUID();
                PlanTentativeConferenceRequest request = createCommandRequest(commandId);


                Stream<Event> stream = Stream.of(createSampleEvent());

                long start = System.nanoTime();
                transactionTemplate.executeWithoutResult(_ -> realPersister.saveCommand(commandId, request));
                transactionTemplate.executeWithoutResult(_ -> eventStore.append(stream, commandId));
                long duration = System.nanoTime() - start;

                latenciesNanos.add(duration);
            }

            long totalDurationNanos = System.nanoTime() - totalStart;

            reportMetrics(measuredIterations, totalDurationNanos, latenciesNanos);
        } catch (Exception e) {
            fail("Performance test failed", e);
        }
    }

    private PlanTentativeConferenceRequest createCommandRequest(UUID commandId) {
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(commandId.toString());
        request.setName("Test Conference");
        request.setStartDate(LocalDateTime.now().plusDays(10));
        request.setEndDate(LocalDateTime.now().plusDays(12));
        request.setVenueName("Test Venue");
        request.setVenueStreet("Street");
        request.setVenueCity("City");
        request.setVenueCountry("Country");
        request.setVenuePostalCode("12345");
        return request;
    }

    private Event createSampleEvent() {
        return new ConferenceTentativelyPlanned(
                ConferenceId.of(UUID.randomUUID()),
                "Test Conference",
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 3, 17, 0),
                "Test Venue",
                new Address("Street", "City", null, "Country", "12345")
        );
    }

    private void reportMetrics(int iterations, long totalTimeNanos, List<Long> latencies) {
        double totalTimeMs = totalTimeNanos / 1_000_000.0;
        double throughputPerSecond = iterations / (totalTimeNanos / 1_000_000_000.0);

        double avgLatencyMs = latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0) / 1_000_000.0;

        List<Long> sorted = latencies.stream().sorted().toList();
        double p50Ms = sorted.get((int) (iterations * 0.50)) / 1_000_000.0;
        double p95Ms = sorted.get((int) (iterations * 0.95)) / 1_000_000.0;
        double p99Ms = sorted.get((int) (iterations * 0.99)) / 1_000_000.0;

        System.out.println("\n=== SPRING BOOT DB BENCHMARK RESULTS ===");
        System.out.printf("Total Iterations:  %d\n", iterations);
        System.out.printf("Total Time:        %.2f ms\n", totalTimeMs);
        System.out.printf("Throughput:        %.2f ops/sec\n", throughputPerSecond);
        System.out.println("--- Latency Percentiles ---");
        System.out.printf("Average Latency:   %.2f ms\n", avgLatencyMs);
        System.out.printf("p50 (Median):       %.2f ms\n", p50Ms);
        System.out.printf("p95 (Worst 5%%):    %.2f ms\n", p95Ms);
        System.out.printf("p99 (Worst 1%%):    %.2f ms\n", p99Ms);
        System.out.println("========================================\n");
    }
}