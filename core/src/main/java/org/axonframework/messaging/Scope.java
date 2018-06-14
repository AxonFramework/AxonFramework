package org.axonframework.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Describes functionality off processes which can be 'in scope', like the
 * {@link org.axonframework.commandhandling.model.AggregateLifecycle} or
 * {@link org.axonframework.eventhandling.saga.SagaLifecycle}.
 *
 * @author Steven van Beelen
 * @see org.axonframework.commandhandling.model.AggregateLifecycle
 * @see org.axonframework.eventhandling.saga.SagaLifecycle
 * @since 3.3
 */
public abstract class Scope {

    private static final Logger logger = LoggerFactory.getLogger(Scope.class);

    private static final ThreadLocal<Deque<Scope>> CURRENT_SCOPE = ThreadLocal.withInitial(LinkedList::new);

    /**
     * Retrieve the current {@link Scope}.
     *
     * @param <S> a type implementing {@link Scope}
     * @return the current {@link Scope}
     *
     * @throws IllegalStateException in case no current {@link Scope} is active, in which case #startScope() should be
     *                               called first
     */
    @SuppressWarnings("unchecked")
    public static <S extends Scope> S getCurrentScope() throws IllegalStateException {
        try {
            return (S) CURRENT_SCOPE.get().getFirst();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Cannot request current Scope if no Scope has been started yet");
        }
    }

    /**
     * Provide a description of the current {@link Scope}.
     *
     * @return a {@link ScopeDescriptor} describing the current {@link Scope}
     */
    public static ScopeDescriptor describeCurrentScope() {
        return getCurrentScope().describeScope();
    }

    /**
     * Start a {@link Scope} by adding {@code this} to a {@link java.util.Deque} contained in a
     * {@link java.lang.ThreadLocal}.
     */
    protected void startScope() {
        CURRENT_SCOPE.get().push(this);
    }

    /**
     * End a {@link Scope} by removing {@code this} from a {@link java.util.Deque} contained in a
     * {@link java.lang.ThreadLocal}.
     * If {@code this} isn't on the top of the Deque, an {@link IllegalStateException} will be thrown, as that signals a
     * process is trying to end somebody else's scope.
     * If the Deque is empty, it will be removed from the ThreadLocal.
     */
    protected void endScope() {
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

    /**
     * Provide a description of this {@link Scope}.
     *
     * @return a {@link ScopeDescriptor} describing this {@link Scope}
     */
    public abstract ScopeDescriptor describeScope();
}
