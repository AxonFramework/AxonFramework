package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Assert;
import org.axonframework.messaging.CorrelationDataProvider;
import org.axonframework.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Allard Buijze
 */
public abstract class AbstractUnitOfWork implements UnitOfWork {

    private static final Logger logger = LoggerFactory.getLogger(AbstractUnitOfWork.class);
    private final UnitOfWorkListenerCollection listeners = new UnitOfWorkListenerCollection();
    private final Map<String, Object> resources = new HashMap<>();
    private final Queue<CorrelationDataProvider<Message<?>>> correlationDataProviders = new ArrayDeque<>();
    private Phase phase = Phase.NOT_STARTED;
    private UnitOfWork parentUnitOfWork;

    @Override
    public void commit() {
        logger.debug("Committing Unit Of Work");
        Assert.state(phase.isStarted(), "UnitOfWork is not started");
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
                listeners.invokeListeners(this, this::setPhase, Phase.PREPARE_COMMIT, Phase.COMMIT);
            } catch (Exception e) {
                listeners.invokeRollbackListeners(this, e, this::setPhase);
                throw e;
            }

            if (phase == Phase.COMMIT) {
                listeners.invokeListeners(this, this::setPhase, Phase.AFTER_COMMIT);
            }
        } finally {
            listeners.invokeListeners(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED);
        }
    }

    private void commitAsNested() {
        UnitOfWork root = root();
        try {
            listeners.invokeListeners(this, p -> this.phase = p, Phase.PREPARE_COMMIT, Phase.COMMIT);
            root.afterCommit(u -> listeners.invokeListeners(this, this::setPhase, Phase.AFTER_COMMIT));
            root.onRollback((u, e) -> listeners.invokeRollbackListeners(this, e, this::setPhase));
        } catch (Exception e) {
            listeners.invokeRollbackListeners(this, e, this::setPhase);
            throw e;
        }
    }

    protected void setPhase(Phase phase) {
        this.phase = phase;
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public Optional<UnitOfWork> parent() {
        return Optional.ofNullable(parentUnitOfWork);
    }

    @Override
    public void rollback(Throwable cause) {
        if (cause != null && logger.isInfoEnabled()) {
            logger.debug("Rollback requested for Unit Of Work due to exception. ", cause);
        } else if (logger.isInfoEnabled()) {
            logger.debug("Rollback requested for Unit Of Work for unknown reason.");
        }

        try {
            listeners.invokeRollbackListeners(this, cause, this::setPhase);
            if (parentUnitOfWork == null) {
                // rollback as root
                listeners.invokeListeners(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED);
            }
        } finally {
            CurrentUnitOfWork.clear(this);
        }
    }

    @Override
    public void start() {
        Assert.state(Phase.NOT_STARTED.equals(phase), "UnitOfWork is already started");
        if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.parentUnitOfWork = CurrentUnitOfWork.get();
            root().onCleanup(u -> listeners.invokeListeners(this, this::setPhase, Phase.CLEANUP, Phase.CLOSED));
        }
        logger.debug("Registering Unit Of Work as CurrentUnitOfWork");
        setPhase(Phase.STARTED);
        CurrentUnitOfWork.set(this);
    }

    @Override
    public Map<String, Object> resources() {
        return resources;
    }

    @Override
    public void registerCorrelationDataProvider(CorrelationDataProvider<Message<?>> correlationDataProvider) {
        correlationDataProviders.add(correlationDataProvider);
    }

    @Override
    public Map<String, ?> getCorrelationData() {
        if (correlationDataProviders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>();
        for (CorrelationDataProvider<Message<?>> correlationDataProvider : correlationDataProviders) {
            result.putAll(correlationDataProvider.correlationDataFor(getMessage()));
        }
        return result;
    }

    @Override
    public Phase phase() {
        return phase;
    }

    protected UnitOfWorkListenerCollection listeners() {
        return listeners;
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
        listeners.addRollbackListener(handler);
    }

    @Override
    public void onCleanup(Consumer<UnitOfWork> handler) {
        addListener(Phase.CLEANUP, handler);
    }

    protected void addListener(Phase phase, Consumer<UnitOfWork> handler) {
        Assert.state(!phase.isBefore(this.phase), "Cannot register a listener for phase: " + phase
                + " because the Unit of Work is already in a later phase: " + this.phase);
        listeners.addListener(phase, handler);
    }
}
