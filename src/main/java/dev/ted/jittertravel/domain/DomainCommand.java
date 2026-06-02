package dev.ted.jittertravel.domain;

import java.util.stream.Stream;

public interface DomainCommand<C extends DecisionContext> {
    Stream<? extends Event> execute(C context);
}
