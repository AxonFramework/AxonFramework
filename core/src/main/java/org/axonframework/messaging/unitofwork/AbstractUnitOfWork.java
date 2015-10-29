package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.metadata.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Abstract implementation of the Unit of Work. It provides default implementations of all methods related to the
 * processing of a Message.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public abstract class AbstractUnitOfWork implements UnitOfWork {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUnitOfWork.class);
    private final UnitOfWorkHandlerCollection listeners = new UnitOfWorkHandlerCollection();
    private final Map<String, Object> resources = new HashMap<>();
    private final Queue<CorrelationDataProvider> correlationDataProviders = new ArrayDeque<>();
    private Phase phase = Phase.NOT_STARTED;
    private UnitOfWork parentUnitOfWork;
    private ExecutionResult executionResult;

    @Override
    public void start() {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting Unit Of Work");
        }
        Assert.state(Phase.NOT_STARTED.equals(phase), "UnitOfWork is already started");
        if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.parentUnitOfWork = CurrentUnitOfWork.get();
            root().onCleanup(u -> listeners.invokeHandlers(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED));
        }
        setPhase(Phase.STARTED);
        CurrentUnitOfWork.set(this);
    }

    @Override
    public void commit() {
        if (logger.isDebugEnabled()) {
            logger.debug("Committing Unit Of Work");
        }
        Assert.state(phase == Phase.STARTED, String.format("The UnitOfWork is in an incompatible phase: %s", phase));
        try {
            if (parentUnitOfWork != null) {
                commitAsNested();
            } else {
                commitAsRoot();
            }
        } finally {
            CurrentUnitOfWork.clear(this);
        }
    }

    private void commitAsRoot() {
        try {
            try {
                listeners.invokeHandlers(this, this::setPhase, Phase.PREPARE_COMMIT, Phase.COMMIT);
            } catch (Exception e) {
                listeners.invokeRollbackListeners(this, e, this::setPhase);
                throw e;
            }

            if (phase == Phase.COMMIT) {
                listeners.invokeHandlers(this, this::setPhase, Phase.AFTER_COMMIT);
            }
        } finally {
            listeners.invokeHandlers(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED);
        }
    }

    private void commitAsNested() {
        UnitOfWork root = root();
        try {
            listeners.invokeHandlers(this, p -> this.phase = p, Phase.PREPARE_COMMIT, Phase.COMMIT);
            root.afterCommit(u -> listeners.invokeHandlers(this, this::setPhase, Phase.AFTER_COMMIT));
            root.onRollback((u, e) -> listeners.invokeRollbackListeners(this, e, this::setPhase));
        } catch (Exception e) {
            listeners.invokeRollbackListeners(this, e, this::setPhase);
            throw e;
        }
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable cause) {
        if (logger.isDebugEnabled()) {
            logger.debug("Rolling back Unit Of Work.", cause);
        }
        Assert.state(isActive() && phase.isBefore(Phase.ROLLBACK),
                String.format("The UnitOfWork is in an incompatible phase: %s", phase));
        try {
            listeners.invokeRollbackListeners(this, cause, this::setPhase);
            if (parentUnitOfWork == null) {
                // rollback as root
                listeners.invokeHandlers(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED);
            }
        } finally {
            CurrentUnitOfWork.clear(this);
        }
    }

    @Override
    public Optional<UnitOfWork> parent() {
        return Optional.ofNullable(parentUnitOfWork);
    }

    @Override
    public Map<String, Object> resources() {
        return resources;
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
    public Phase phase() {
        return phase;
    }

    @Override
    public void onPrepareCommit(Consumer<UnitOfWork> handler) {
        addListener(Phase.PREPARE_COMMIT, handler);
    }

    @Override
    public void onCommit(Consumer<UnitOfWork> handler) {
        addListener(Phase.COMMIT, handler);
    }

    @Override
    public void afterCommit(Consumer<UnitOfWork> handler) {
        addListener(Phase.AFTER_COMMIT, handler);
    }

    @Override
    public void onRollback(BiConsumer<UnitOfWork, Throwable> handler) {
        Assert.state(!Phase.ROLLBACK.isBefore(phase),
                     "Cannot register a rollback listener. The Unit of Work is already after commit.");
        listeners.addRollbackHandler(handler);
    }

    @Override
    public void onCleanup(Consumer<UnitOfWork> handler) {
        addListener(Phase.CLEANUP, handler);
    }

    @Override
    public void execute(Runnable task, RollbackConfiguration rollbackConfiguration) {
        try {
            executeWithResult(() -> {
                task.run();
                return null;
            }, rollbackConfiguration);
        } catch (Exception e) {
            throw (RuntimeException) e;
        }
    }

    @Override
    public <R> R executeWithResult(Callable<R> task, RollbackConfiguration rollbackConfiguration) throws Exception {
        if (phase() == Phase.NOT_STARTED) {
            start();
        }
        Assert.state(phase() == Phase.STARTED, String.format("The UnitOfWork has an incompatible phase: %s", phase()));
        R result;
        try {
            result = task.call();
        } catch (Exception e) {
            executionResult = new ExecutionResult(e);
            if (rollbackConfiguration.rollBackOn(e)) {
                rollback(e);
            } else {
                commit();
            }
            throw e;
        }
        executionResult = new ExecutionResult(result);
        commit();
        return result;
    }

    @Override
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }

    /**
     * Register the given <code>handler</code> with the Unit of Work. The handler will be invoked when the
     * Unit of Work changes its phase to the given <code>phase</code>.
     *
     * @param phase     the Phase of the Unit of Work at which to invoke the handler
     * @param handler   the handler to add
     */
    protected void addListener(Phase phase, Consumer<UnitOfWork> handler) {
        Assert.state(!phase.isBefore(this.phase), "Cannot register a listener for phase: " + phase
                + " because the Unit of Work is already in a later phase: " + this.phase);
        listeners.addHandler(phase, handler);
    }

    /**
     * Set the the phase of the Unit of Work to given <code>phase</code>.
     *
     * @param phase The new phase of the Unit of Work
     */
    protected void setPhase(Phase phase) {
        this.phase = phase;
    }

    /**
     * Returns the collection of handlers for this Unit of Work.
     *
     * @return the collection of handlers for this Unit of Work.
     */
    protected UnitOfWorkHandlerCollection handlers() {
        return listeners;
    }
}
