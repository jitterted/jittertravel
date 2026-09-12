package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

// Full-context integration tests run under the default (secured) profile — the only security
// chain there is. These tests never hit secured web routes, so they need no authentication;
// they only supply TED_PASSWORD/FAMILY_PASSWORD/REMEMBER_ME_KEY so the userDetailsService and
// rememberMeServices beans can start. The testcontainer @ServiceConnection supplies the datasource.
@SuppressWarnings("SqlWithoutWhere")
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "TED_PASSWORD=test",
        "FAMILY_PASSWORD=test",
        "REMEMBER_ME_KEY=test-remember-me-key"
})
// ensure the database is empty before each test by running it in its own transaction
@Sql(
        statements = "TRUNCATE TABLE event_log, command_log RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
public abstract class AbstractTestcontainerIntegrationTest {

    // ObjectProvider, not EventStore: PostgresPersisterTest's @JdbcTest slice has no such bean.
    @Autowired
    private ObjectProvider<EventStore> eventStore;

    /**
     * Returns the in-memory half of the event store to a known state, and proves it. The @Sql above
     * has just emptied the tables, but EventStore's list is filled at boot and otherwise only
     * appended to, so no truncation reaches it: without this reload a test sees events written by
     * whatever ran before it — in this JVM, or (the container is reused) in a previous run.
     *
     * <p>The assertion is the point. Whatever leaks state next fails here, naming this guard,
     * instead of surfacing as one unrelated test that fails about half the time.
     *
     * <p>Do not "fix" a failure here by arranging fixtures so they stop colliding: a component that
     * cannot be returned to a known state is one the app cannot return to a known state either (see
     * CLAUDE.md, "Every test is isolated").
     */
    @BeforeEach
    void returnTheEventStoreToAKnownState() {
        eventStore.ifAvailable(store -> {
            store.reload();
            assertThat(store.findAll())
                    .as("every test starts with an empty event store")
                    .isEmpty();
        });
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18-alpine")
                    .withCreateContainerCmdModifier(cmd -> cmd.withName("jittertravel-test-postgres"))
                    .withLabel("app", "jittertravel")
                    .withLabel("purpose", "integration-tests")
                    .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off")
                    .withReuse(true);
}
