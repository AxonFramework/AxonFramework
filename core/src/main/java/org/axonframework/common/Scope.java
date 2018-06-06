package org.axonframework.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Describes functionality off processes which can be 'in scope', like the
 * {@link org.axonframework.commandhandling.model.AggregateLifecycle} or
 * {@link org.axonframework.eventhandling.saga.SagaLifecycle}.
 *
 * @author Steven van Beelen
 * @since 3.3
 */
public abstract class Scope {

    private static final Logger logger = LoggerFactory.getLogger(Scope.class);

    private static final ThreadLocal<Deque<Scope>> CURRENT_SCOPE = ThreadLocal.withInitial(LinkedList::new);

    @SuppressWarnings("unchecked") // TODO figure out if we can make this a checked call
    public static <S extends Scope> S getCurrentScope() {
        return (S) CURRENT_SCOPE.get().getFirst();
    }

    public void startScope() {
        CURRENT_SCOPE.get().push(this);
    }

    public void endScope() {
        if (this != CURRENT_SCOPE.get().peek()) {
            throw new IllegalStateException(
                    "Incorrectly trying to end another Scope then which Scope the calling process is contained in."
            );
        }
        CURRENT_SCOPE.get().pop();

        if (CURRENT_SCOPE.get().isEmpty()) {
            logger.info("Clearing out ThreadLocal current Scope, as no Scopes are present");
            CURRENT_SCOPE.remove();
        }
    }
}
