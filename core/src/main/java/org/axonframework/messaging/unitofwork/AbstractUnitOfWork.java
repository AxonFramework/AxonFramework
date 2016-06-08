package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.metadata.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Abstract implementation of the Unit of Work. It provides default implementations of all methods related to the
 * processing of a Message.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public abstract class AbstractUnitOfWork<T extends Message<?>> implements UnitOfWork<T> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUnitOfWork.class);
    private final Map<String, Object> resources = new HashMap<>();
    private final Collection<CorrelationDataProvider> correlationDataProviders = new LinkedHashSet<>();
    private UnitOfWork<?> parentUnitOfWork;
    private Phase phase = Phase.NOT_STARTED;
    private boolean rolledBack;

    @Override
    public void start() {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting Unit Of Work");
        }
        Assert.state(Phase.NOT_STARTED.equals(phase()), "UnitOfWork is already started");
        onRollback(u -> rolledBack = true);
        if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.parentUnitOfWork = CurrentUnitOfWork.get();
            root().onCleanup(u -> changePhase(Phase.CLEANUP, Phase.CLOSED));
        }
        changePhase(Phase.STARTED);
        CurrentUnitOfWork.set(this);
    }

    @Override
    public void commit() {
        if (logger.isDebugEnabled()) {
            logger.debug("Committing Unit Of Work");
        }
        Assert.state(phase() == Phase.STARTED, String.format("The UnitOfWork is in an incompatible phase: %s", phase()));
        Assert.state(isCurrent(), "The UnitOfWork is not the current Unit of Work");
        try {
            if (isRoot()) {
                commitAsRoot();
            } else {
                commitAsNested();
            }
        } finally {
            CurrentUnitOfWork.clear(this);
        }
    }

    private void commitAsRoot() {
        try {
            try {
                changePhase(Phase.PREPARE_COMMIT, Phase.COMMIT);
            } catch (Exception e) {
                setRollbackCause(e);
                changePhase(Phase.ROLLBACK);
                throw e;
            }
            if (phase() == Phase.COMMIT) {
                changePhase(Phase.AFTER_COMMIT);
            }
        } finally {
            changePhase(Phase.CLEANUP, Phase.CLOSED);
        }
    }

    private void commitAsNested() {
        UnitOfWork<?> root = root();
        try {
            changePhase(Phase.PREPARE_COMMIT, Phase.COMMIT);
            root.afterCommit(u -> changePhase(Phase.AFTER_COMMIT));
            root.onRollback(u -> changePhase(Phase.ROLLBACK));
        } catch (Exception e) {
            setRollbackCause(e);
            changePhase(Phase.ROLLBACK);
            throw e;
        }
    }

    @Override
    public void rollback(Throwable cause) {
        if (logger.isDebugEnabled()) {
            logger.debug("Rolling back Unit Of Work.", cause);
        }
        Assert.state(isActive() && phase().isBefore(Phase.ROLLBACK),
                String.format("The UnitOfWork is in an incompatible phase: %s", phase()));
        Assert.state(isCurrent(), "The UnitOfWork is not the current Unit of Work");
        try {
            setRollbackCause(cause);
            changePhase(Phase.ROLLBACK);
            if (isRoot()) {
                changePhase(Phase.CLEANUP, Phase.CLOSED);
            }
        } finally {
            CurrentUnitOfWork.clear(this);
        }
    }

    @Override
    public Optional<UnitOfWork<?>> parent() {
        return Optional.ofNullable(parentUnitOfWork);
    }

    @Override
    public Map<String, Object> resources() {
        return resources;
    }

    @Override
    public boolean isRolledBack() {
        return rolledBack;
    }

    @Override
    public void registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider) {
        correlationDataProviders.add(correlationDataProvider);
    }

    @Override
    public MetaData getCorrelationData() {
        if (correlationDataProviders.isEmpty()) {
            return MetaData.emptyInstance();
        }
        Map<String, Object> result = new HashMap<>();
        for (CorrelationDataProvider correlationDataProvider : correlationDataProviders) {
            final Map<String, ?> extraData = correlationDataProvider.correlationDataFor(getMessage());
            if (extraData != null) {
                result.putAll(extraData);
            }
        }
        return MetaData.from(result);
    }

    @Override
    public void onPrepareCommit(Consumer<UnitOfWork<T>> handler) {
        addHandler(Phase.PREPARE_COMMIT, handler);
    }

    @Override
    public void onCommit(Consumer<UnitOfWork<T>> handler) {
        addHandler(Phase.COMMIT, handler);
    }

    @Override
    public void afterCommit(Consumer<UnitOfWork<T>> handler) {
        addHandler(Phase.AFTER_COMMIT, handler);
    }

    @Override
    public void onRollback(Consumer<UnitOfWork<T>> handler) {
        addHandler(Phase.ROLLBACK, handler);
    }

    @Override
    public void onCleanup(Consumer<UnitOfWork<T>> handler) {
        addHandler(Phase.CLEANUP, handler);
    }

    @Override
    public Phase phase() {
        return phase;
    }

    /**
     * Overwrite the current phase with the given <code>phase</code>.
     *
     * @param phase the new phase of the Unit of Work
     */
    protected void setPhase(Phase phase) {
        this.phase = phase;
    }

    /**
     * Ask the unit of work to transition to the given <code>phases</code> sequentially. In each of the phases the
     * unit of work is responsible for invoking the handlers attached to each phase.
     * <p/>
     * By default this sets the Phase and invokes the handlers attached to the phase.
     *
     * @param phases The phases to transition to in sequential order
     */
    protected void changePhase(Phase... phases) {
        for (Phase phase : phases) {
            setPhase(phase);
            notifyHandlers(phase);
        }
    }

    /**
     * Notify the handlers attached to the given <code>phase</code>.
     *
     * @param phase The phase for which to invoke registered handlers.
     */
    protected abstract void notifyHandlers(Phase phase);

    /**
     * Register the given <code>handler</code> with the Unit of Work. The handler will be invoked when the
     * Unit of Work changes its phase to the given <code>phase</code>.
     *
     * @param phase     the Phase of the Unit of Work at which to invoke the handler
     * @param handler   the handler to add
     */
    protected abstract void addHandler(Phase phase, Consumer<UnitOfWork<T>> handler);

    /**
     * Set the execution result of processing the current {@link #getMessage() Message}.
     *
     * @param executionResult the ExecutionResult of the currently handled Message
     */
    protected abstract void setExecutionResult(ExecutionResult executionResult);

    /**
     * Sets the cause for rolling back this Unit of Work.
     *
     * @param cause The cause for rolling back this Unit of Work
     */
    protected abstract void setRollbackCause(Throwable cause);
}
