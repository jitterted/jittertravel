package dev.ted.jittertravel.infrastructure;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Full-context integration tests run under the `local` profile: the permissive dev security
// chain (no required TED_PASSWORD/FAMILY_PASSWORD). The testcontainer @ServiceConnection still
// supplies the datasource, overriding the local profile's localhost defaults.
@ActiveProfiles("local")
@SuppressWarnings("SqlWithoutWhere")
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0"
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
