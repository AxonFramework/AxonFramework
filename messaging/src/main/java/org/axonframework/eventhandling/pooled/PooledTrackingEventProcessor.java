package org.axonframework.eventhandling.pooled;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.AbstractEventProcessor;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.monitoring.MessageMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * A special type of Event Processor that tracks events from a {@link StreamableMessageSource}, similar to the
 * {@link org.axonframework.eventhandling.TrackingEventProcessor}, but that does all processing
 */
public class PooledTrackingEventProcessor extends AbstractEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PooledTrackingEventProcessor.class);

    private final String name;
    private final TokenStore tokenStore;
    private final ScheduledExecutorService workerExecutor;
    private final Coordinator coordinator;
    private final TransactionManager transactionManager;
    private final ErrorHandler errorHandler;
    private final int initialSegmentCount;
    private final Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken;
    private final StreamableMessageSource<TrackedEventMessage<?>> messageSource;

    public PooledTrackingEventProcessor(PooledTrackingEventProcessor.Builder builder) {
        super(builder);
        this.transactionManager = builder.transactionManager;
        this.workerExecutor = builder.workerExecutor;
        ScheduledExecutorService coordinatorService = builder.coordinatorExecutor;
        this.name = builder.name();
        this.tokenStore = builder.tokenStore;
        this.errorHandler = PropagatingErrorHandler.instance();
        this.initialSegmentCount = builder.initialSegmentCount;
        this.initialToken = builder.initialToken;
        messageSource = builder.messageSource;
        coordinator = new Coordinator(name,
                                      messageSource,
                                      tokenStore,
                                      transactionManager,
                                      this::spawnWorker,
                                      coordinatorService);
    }

    @StartHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    @Override
    public void start() {
        logger.info("PooledTrackingEventProcessor {} starting", name);
        transactionManager.executeInTransaction(() -> {

            int[] ints = tokenStore.fetchSegments(name);
            if (ints == null || ints.length == 0) {
                logger.info("Initializing segments for {} ({} segments)", name, 8);
                tokenStore.initializeTokenSegments(name, initialSegmentCount,
                                                   initialToken.apply(messageSource));
            }
        });
        coordinator.start();
    }

    @Override
    public void shutDown() {
        shutdownAsync().join();
    }

    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    @Override
    public CompletableFuture<Void> shutdownAsync() {
        return coordinator.stop();
    }

    public Set<Segment> activeSegments() {
        return coordinator.activeSegments();
    }

    public static Builder builder() {
        return new Builder();
    }

    private WorkPackage spawnWorker(Segment segment, TrackingToken initialToken) {
        return new WorkPackage(name,
                               segment,
                               initialToken,
                               this::processInUnitOfWork,
                               this::canHandle,
                               workerExecutor,
                               tokenStore,
                               transactionManager,
                               errorHandler);
    }

    public static class Builder extends AbstractEventProcessor.Builder {

        private int initialSegmentCount = 32;
        private Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken = StreamableMessageSource::createTailToken;
        private ScheduledExecutorService coordinatorExecutor;
        private ScheduledExecutorService workerExecutor;
        private TokenStore tokenStore;
        private StreamableMessageSource<TrackedEventMessage<?>> messageSource;
        private TransactionManager transactionManager;

        protected Builder() {
        }

        public Builder coordinatorExecutor(ScheduledExecutorService executorService) {
            this.coordinatorExecutor = executorService;
            return this;
        }

        public Builder workerExecutorService(ScheduledExecutorService executorService) {
            this.workerExecutor = executorService;
            return this;
        }

        public Builder initialSegmentCount(int initialSegmentCount) {
            this.initialSegmentCount = initialSegmentCount;
            return this;
        }

        public Builder initialToken(Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken) {
            this.initialToken = initialToken;
            return this;
        }

        @Override
        public Builder eventHandlerInvoker(EventHandlerInvoker eventHandlerInvoker) {
            super.eventHandlerInvoker(eventHandlerInvoker);
            return this;
        }

        @Override
        public Builder rollbackConfiguration(RollbackConfiguration rollbackConfiguration) {
            super.rollbackConfiguration(rollbackConfiguration);
            return this;
        }

        @Override
        public Builder errorHandler(ErrorHandler errorHandler) {
            super.errorHandler(errorHandler);
            return this;
        }

        @Override
        public Builder messageMonitor(MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        public Builder name(String name) {
            super.name(name);
            this.name = name;
            return this;
        }

        public Builder tokenStore(TokenStore tokenStore) {
            this.tokenStore = tokenStore;
            return this;
        }

        public Builder messageSource(StreamableMessageSource<TrackedEventMessage<?>> messageSource) {
            this.messageSource = messageSource;
            return this;
        }

        public Builder transactionManager(TransactionManager transactionManager) {
            this.transactionManager = transactionManager;
            return this;
        }

        @Override
        protected void validate() throws AxonConfigurationException {
            // TODO - Validate all settings
            super.validate();
        }

        public PooledTrackingEventProcessor build() {
            return new PooledTrackingEventProcessor(this);
        }

        protected String name() {
            return name;
        }
    }
}
