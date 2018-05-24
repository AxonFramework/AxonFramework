/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.deadline;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.scheduling.ScheduleToken;
import org.axonframework.eventhandling.scheduling.java.SimpleScheduleToken;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.axonframework.common.Assert.notNull;

/**
 * Implementation of {@link DeadlineManager} which uses Java's {@link ScheduledExecutorService} as scheduling and
 * triggering mechanism.
 * <p>
 * Note that this mechanism is non-persistent. Scheduled tasks will be lost then the JVM is shut down, unless special
 * measures have been taken to prevent that. For more flexible and powerful scheduling options, see {@link
 * org.axonframework.deadline.quartz.QuartzDeadlineManager}.
 * </p>
 *
 * @author Milan Savic
 * @since 3.3
 */
public class SimpleDeadlineManager implements DeadlineManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleDeadlineManager.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final TransactionManager transactionManager;
    private final Map<String, Future<?>> tokens = new ConcurrentHashMap<>();

    private final DeadlineTargetLoader deadlineTargetLoader;

    /**
     * Initializes SimpleDeadlineManager with {@code deadlineTargetLoader}. {@link NoTransactionManager} is used as
     * transaction manager and {@link Executors#newSingleThreadScheduledExecutor()} is used as scheduled executor
     * service.
     *
     * @param deadlineTargetLoader Responsible for loading the end target capable of handling the deadline message when
     *                             deadline is not met
     */
    public SimpleDeadlineManager(DeadlineTargetLoader deadlineTargetLoader) {
        this(Executors.newSingleThreadScheduledExecutor(), NoTransactionManager.INSTANCE, deadlineTargetLoader);
    }

    /**
     * Initializes SimpleDeadlineManager with {@code transactionManager} and {@code deadlineTargetLoader}. {@link
     * Executors#newSingleThreadScheduledExecutor()} is used as scheduled executor service.
     *
     * @param transactionManager   The transaction manager used to manage transaction during processing of {@link
     *                             DeadlineMessage} when deadline is not met
     * @param deadlineTargetLoader Responsible for loading the end target capable of handling the deadline message when
     *                             deadline is not met
     */
    public SimpleDeadlineManager(TransactionManager transactionManager, DeadlineTargetLoader deadlineTargetLoader) {
        this(Executors.newSingleThreadScheduledExecutor(), transactionManager, deadlineTargetLoader);
    }

    /**
     * Initializes SimpleDeadlineManager.
     *
     * @param scheduledExecutorService Java's service used for scheduling and triggering deadlines
     * @param transactionManager       The transaction manager used to manage transaction during processing of {@link
     *                                 DeadlineMessage} when deadline is not met
     * @param deadlineTargetLoader     Responsible for loading the end target capable of handling the deadline message
     *                                 when deadline is not met
     */
    public SimpleDeadlineManager(ScheduledExecutorService scheduledExecutorService,
                                 TransactionManager transactionManager,
                                 DeadlineTargetLoader deadlineTargetLoader) {
        notNull(scheduledExecutorService, () -> "scheduledExecutorService may not be null");
        notNull(transactionManager, () -> "transactionManager may not be null");
        notNull(deadlineTargetLoader, () -> "deadlineTargetLoader may not be null");
        this.scheduledExecutorService = scheduledExecutorService;
        this.transactionManager = transactionManager;
        this.deadlineTargetLoader = deadlineTargetLoader;
    }

    @Override
    public void schedule(Instant triggerDateTime, DeadlineContext deadlineContext,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        schedule(Duration.between(Instant.now(), triggerDateTime), deadlineContext, deadlineInfo, scheduleToken);
    }

    @Override
    public void schedule(Duration triggerDuration, DeadlineContext deadlineContext,
                         Object deadlineInfo, ScheduleToken scheduleToken) {
        SimpleScheduleToken simpleScheduleToken = convert(scheduleToken);
        ScheduledFuture<?> future = scheduledExecutorService.schedule(new DeadlineTask(simpleScheduleToken.getTokenId(),
                                                                                       deadlineInfo,
                                                                                       deadlineContext),
                                                                      triggerDuration.toMillis(),
                                                                      TimeUnit.MILLISECONDS);
        tokens.put(simpleScheduleToken.getTokenId(), future);
    }

    @Override
    public ScheduleToken generateToken() {
        return new SimpleScheduleToken(IdentifierFactory.getInstance().generateIdentifier());
    }

    @Override
    public void cancelSchedule(ScheduleToken scheduleToken) {
        Future<?> future = tokens.remove(convert(scheduleToken).getTokenId());
        if (future != null) {
            future.cancel(false);
        }
    }

    private SimpleScheduleToken convert(ScheduleToken scheduleToken) {
        if (!SimpleScheduleToken.class.isInstance(scheduleToken)) {
            throw new IllegalArgumentException("The given ScheduleToken was not provided by this scheduler");
        }
        return (SimpleScheduleToken) scheduleToken;
    }

    private class DeadlineTask implements Runnable {

        private final String tokenId;
        private final Object deadlineInfo;
        private final DeadlineContext deadlineContext;

        private DeadlineTask(String tokenId, Object deadlineInfo, DeadlineContext deadlineContext) {
            this.tokenId = tokenId;
            this.deadlineInfo = deadlineInfo;
            this.deadlineContext = deadlineContext;
        }

        @Override
        public void run() {
            DeadlineMessage<?> deadlineMessage = GenericDeadlineMessage.asDeadlineMessage(deadlineInfo);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Triggered deadline");
            }
            try {
                UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(null);
                Transaction transaction = transactionManager.startTransaction();
                unitOfWork.onCommit(u -> transaction.commit());
                unitOfWork.onRollback(u -> transaction.rollback());
                unitOfWork.execute(() -> deadlineTargetLoader.load(deadlineContext).handle(deadlineMessage));
            } finally {
                tokens.remove(tokenId);
            }
        }
    }
}
