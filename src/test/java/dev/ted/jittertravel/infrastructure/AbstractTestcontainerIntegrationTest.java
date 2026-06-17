package dev.ted.jittertravel.infrastructure;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Full-context integration tests run under the default (secured) profile — the only security
// chain there is. These tests never hit secured web routes, so they need no authentication;
// they only supply TED_PASSWORD/FAMILY_PASSWORD so the userDetailsService bean can start. The
// testcontainer @ServiceConnection supplies the datasource.
@SuppressWarnings("SqlWithoutWhere")
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "TED_PASSWORD=test",
        "FAMILY_PASSWORD=test"
})
// ensure the database is empty before each test by running it in its own transaction
@Sql(
        statements = "TRUNCATE TABLE event_log, command_log RESTART IDENTITY CASCADE",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
)
public abstract class AbstractTestcontainerIntegrationTest {
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
