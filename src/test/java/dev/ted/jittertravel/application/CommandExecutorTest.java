package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.DecisionContext;
import dev.ted.jittertravel.domain.DomainCommand;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.infrastructure.EventStore;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandExecutorTest {

    private static final UUID COMMAND_ID = UUID.randomUUID();

    @Mock PostgresPersister persister;
    @Mock EventStore eventStore;

    private record TestContext() implements DecisionContext {}

    private record TestEvent() implements Event {}

    @Test
    void persistsCommandAsPendingBeforeExecutingThenAppendsEvents() {
        CommandExecutor executor = new CommandExecutor(persister, eventStore);
        DomainCommand<TestContext> command = _ -> Stream.of(new TestEvent());

        executor.execute(COMMAND_ID, "request", new TestContext(), command);

        InOrder inOrder = inOrder(persister, eventStore);
        inOrder.verify(persister).saveCommand(COMMAND_ID, "request");
        inOrder.verify(eventStore).append(any(), eq(COMMAND_ID));
        verify(persister, never()).markCommandFailed(any(), any(), any());
    }

    @Test
    void marksCommandFailedDomainAndDoesNotAppendWhenExecuteThrows() {
        CommandExecutor executor = new CommandExecutor(persister, eventStore);
        DomainCommand<TestContext> rejecting = _ -> {
            throw new IllegalArgumentException("departure not in future");
        };

        assertThatThrownBy(() -> executor.execute(COMMAND_ID, "request", new TestContext(), rejecting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("departure not in future");

        verify(persister).saveCommand(COMMAND_ID, "request");
        verify(persister).markCommandFailed(COMMAND_ID, "FAILED_DOMAIN", "departure not in future");
        verify(eventStore, never()).append(any(), any());
    }

    @Test
    void marksCommandFailedPersistWhenAppendThrows() {
        CommandExecutor executor = new CommandExecutor(persister, eventStore);
        DomainCommand<TestContext> command = _ -> Stream.of(new TestEvent());
        willThrow(new RuntimeException("db down"))
                .given(eventStore).append(any(), eq(COMMAND_ID));

        assertThatThrownBy(() -> executor.execute(COMMAND_ID, "request", new TestContext(), command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(persister).markCommandFailed(COMMAND_ID, "FAILED_PERSIST", "db down");
    }

    @Test
    void appendEventsPathSavesPendingThenAppends() {
        CommandExecutor executor = new CommandExecutor(persister, eventStore);

        executor.appendEvents(COMMAND_ID, "record", Stream.of(new TestEvent()));

        InOrder inOrder = inOrder(persister, eventStore);
        inOrder.verify(persister).saveCommand(COMMAND_ID, "record");
        inOrder.verify(eventStore).append(any(), eq(COMMAND_ID));
    }
}
