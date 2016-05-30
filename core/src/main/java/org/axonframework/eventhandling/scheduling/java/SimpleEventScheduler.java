/*
 * Copyright (c) 2010-2014. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.common.Assert;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * An {@link EventScheduler} implementation that uses Java's ScheduledExecutorService as scheduling and triggering
 * mechanism.
 * <p/>
 * Note that this mechanism is non-persistent. Scheduled tasks will be lost when the JVM is shut down, unless special
 * measures have been taken to prevent that. For more flexible and powerful scheduling options, see {@link
 * org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler}.
 *
 * @author Allard Buijze
 * @see org.axonframework.eventhandling.scheduling.quartz.QuartzEventScheduler
 * @since 0.7
 */
public class SimpleEventScheduler implements EventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventScheduler.class);

    private final ScheduledExecutorService executorService;
    private final EventBus eventBus;
    private final UnitOfWorkFactory unitOfWorkFactory;
    private final Map<String, Future<?>> tokens = new ConcurrentHashMap<>();

    /**
     * Initialize the SimpleEventScheduler using the given <code>executorService</code> as trigger and execution
     * mechanism, and publishes events to the given <code>eventBus</code>.
     *
     * @param executorService The backing ScheduledExecutorService
     * @param eventBus        The Event Bus on which Events are to be published
     */
    public SimpleEventScheduler(ScheduledExecutorService executorService, EventBus eventBus) {
        this(executorService, eventBus, new DefaultUnitOfWorkFactory());
    }

    /**
     * Initialize the SimpleEventScheduler using the given <code>executorService</code> as trigger and execution
     * mechanism, and publishes events to the given <code>eventBus</code>. The <code>eventTriggerCallback</code> is
     * invoked just before and after publication of a scheduled event.
     *
     * @param executorService   The backing ScheduledExecutorService
     * @param eventBus          The Event Bus on which Events are to be published
     * @param unitOfWorkFactory The factory that creates the Unit of Work to manage transactions
     */
    public SimpleEventScheduler(ScheduledExecutorService executorService, EventBus eventBus,
                                UnitOfWorkFactory unitOfWorkFactory) {
        Assert.notNull(executorService, "executorService may not be null");
        Assert.notNull(eventBus, "eventBus may not be null");
        Assert.notNull(unitOfWorkFactory, "unitOfWorkFactory may not be null");

        this.executorService = executorService;
        this.eventBus = eventBus;
        this.unitOfWorkFactory = unitOfWorkFactory;
    }

    @Override
    public ScheduleToken schedule(ZonedDateTime triggerDateTime, Object event) {
        return schedule(Duration.between(ZonedDateTime.now(), triggerDateTime), event);
    }

    @Override
    public ScheduleToken schedule(Duration triggerDuration, Object event) {
        String tokenId = IdentifierFactory.getInstance().generateIdentifier();
        ScheduledFuture<?> future = executorService.schedule(new PublishEventTask(event, tokenId),
                                                             triggerDuration.toMillis(),
                                                             TimeUnit.MILLISECONDS);
        tokens.put(tokenId, future);
        return new SimpleScheduleToken(tokenId);
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        if (!SimpleScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler.");
        }
        Future<?> future = tokens.remove(((SimpleScheduleToken) scheduleToken).getTokenId());
        if (future != null) {
            future.cancel(false);
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
                UnitOfWork<EventMessage<?>> unitOfWork = unitOfWorkFactory.createUnitOfWork(eventMessage);
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
            EventMessage<?> eventMessage;
            if (event instanceof EventMessage) {
                eventMessage = new GenericEventMessage<>(((EventMessage) event).getPayload(),
                                                               ((EventMessage) event).getMetaData());
            } else {
                eventMessage = new GenericEventMessage<>(event, MetaData.emptyInstance());
            }
            return eventMessage;
        }
    }
}
