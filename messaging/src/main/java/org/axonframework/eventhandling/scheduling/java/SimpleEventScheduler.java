/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.lifecycle.Lifecycle;
import org.axonframework.lifecycle.Phase;
import org.axonframework.messaging.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * An {@link EventScheduler} implementation that uses Java's ScheduledExecutorService as scheduling and triggering
 * mechanism.
 * <p/>
 * Note that this mechanism is non-persistent. Scheduled tasks will be lost when the JVM is shut down, unless special
 * measures have been taken to prevent that. For more flexible and powerful scheduling options, see
 * {@link org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler
 * @since 0.7
 */
public class SimpleEventScheduler implements EventScheduler, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventScheduler.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final EventBus eventBus;
    private final TransactionManager transactionManager;
    private final MessageTypeResolver messageTypeResolver;

    private final Map<String, Future<?>> tokens = new ConcurrentHashMap<>();

    /**
     * Instantiate a {@link SimpleEventScheduler} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link ScheduledExecutorService}, {@link EventBus} and {@link TransactionManager} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleEventScheduler} instance
     */
    protected SimpleEventScheduler(Builder builder) {
        builder.validate();
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.eventBus = builder.eventBus;
        this.transactionManager = builder.transactionManager;
        this.messageTypeResolver = builder.messageTypeResolver;
    }

    /**
     * Instantiate a Builder to be able to create a {@link SimpleEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}. The
     * {@link ScheduledExecutorService} and {@link EventBus} are a <b>hard requirements</b> and as such should be
     * provided.
     *
     * @return a Builder to be able to create a {@link SimpleEventScheduler}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ScheduleToken schedule(Instant triggerDateTime, Object event) {
        return schedule(Duration.between(Instant.now(), triggerDateTime), event);
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        String tokenId = IdentifierFactory.getInstance().generateIdentifier();
        ScheduledFuture<?> future = scheduledExecutorService.schedule(new PublishEventTask(event, tokenId),
                                                                      triggerDuration.toMillis(),
                                                                      TimeUnit.MILLISECONDS);
        tokens.put(tokenId, future);
        return new SimpleScheduleToken(tokenId);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!(scheduleToken instanceof SimpleScheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        Future<?> future = tokens.remove(((SimpleScheduleToken) scheduleToken).getTokenId());
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void registerLifecycleHandlers(@Nonnull LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    @Override
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    /**
     * Builder class to instantiate a {@link SimpleEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}. The
     * {@link ScheduledExecutorService} and {@link EventBus} are a <b>hard requirements</b> and as such should be
     * provided.
     */
    public static class Builder {

        private ScheduledExecutorService scheduledExecutorService;
        private EventBus eventBus;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private MessageTypeResolver messageTypeResolver = new ClassBasedMessageTypeResolver();

        /**
         * Sets the {@link EventBus} used to publish events on to, once the schedule has been met.
         *
         * @param eventBus a {@link EventBus} used to publish events on to, once the schedule has been met
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventBus(EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} used for scheduling and triggering events.
         *
         * @param scheduledExecutorService a {@link ScheduledExecutorService} used for scheduling and triggering events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "ScheduledExecutorService may not be null");
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them on event publication. Defaults
         * to a {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them on event
         *                           publication
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link MessageTypeResolver} used to resolve the {@link QualifiedName} when scheduling
         * {@link EventMessage EventMessages}. If not set, a {@link ClassBasedMessageTypeResolver} is used by default.
         *
         * @param messageTypeResolver The {@link MessageTypeResolver} used to provide the {@link QualifiedName} for
         *                            {@link EventMessage EventMessages}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageNameResolver(MessageTypeResolver messageTypeResolver) {
            assertNonNull(messageTypeResolver, "MessageNameResolver may not be null");
            this.messageTypeResolver = messageTypeResolver;
            return this;
        }

        /**
         * Initializes a {@link SimpleEventScheduler} as specified through this Builder.
         *
         * @return a {@link SimpleEventScheduler} as specified through this Builder
         */
        public SimpleEventScheduler build() {
            return new SimpleEventScheduler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided");
            assertNonNull(scheduledExecutorService,
                          "The ScheduledExecutorService is a hard requirement and should be provided");
        }
    }

    private class PublishEventTask implements Runnable {

        private final Object event;
        private final String tokenId;

        public PublishEventTask(Object event, String tokenId) {
            this.event = event;
            this.tokenId = tokenId;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            EventMessage<?> eventMessage = createMessage();
            if (logger.isDebugEnabled()) {
                logger.debug("Triggered the publication of event [{}]", eventMessage.getPayloadType().getSimpleName());
            }
            try {
                UnitOfWork<EventMessage<?>> unitOfWork = new DefaultUnitOfWork<>(null);
                unitOfWork.attachTransaction(transactionManager);
                unitOfWork.execute(() -> eventBus.publish(eventMessage));
            } finally {
                tokens.remove(tokenId);
            }
        }

        /**
         * Creates a new message for the scheduled event. This ensures that a new identifier and timestamp will always
         * be generated, so that the timestamp will reflect the actual moment the trigger occurred.
         *
         * @return the message to publish
         */
        private EventMessage<?> createMessage() {
            return event instanceof EventMessage
                    ? new GenericEventMessage<>(((EventMessage<?>) event).type(),
                                                ((EventMessage<?>) event).getPayload(),
                                                ((EventMessage<?>) event).getMetaData())
                    : new GenericEventMessage<>(messageTypeResolver.resolve(event), event);
        }
    }
}
