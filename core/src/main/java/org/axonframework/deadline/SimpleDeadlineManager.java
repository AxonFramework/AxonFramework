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

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.axonframework.common.Assert.isFalse;
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
 * @author Steven van Beelen
 * @since 3.3
 */
public class SimpleDeadlineManager implements DeadlineManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDeadlineManager.class);

    private final List<ScopeAware> scopeAwareComponents;
    private final ScheduledExecutorService scheduledExecutorService;
    private final TransactionManager transactionManager;

    private final Map<String, Future<?>> tokens = new ConcurrentHashMap<>();

    /**
     * Initializes SimpleDeadlineManager with {@code scopeAwareComponents} which will load and send messages to
     * {@link org.axonframework.messaging.Scope} implementing components. {@link NoTransactionManager} is used as
     * transaction manager and {@link Executors#newSingleThreadScheduledExecutor()} is used as scheduled executor
     * service.
     *
     * @param scopeAwareComponents a {@link List} of {@link ScopeAware} components which are able to load and send
     *                             Messages to components which implement {@link org.axonframework.messaging.Scope}
     */
    public SimpleDeadlineManager(ScopeAware... scopeAwareComponents) {
        this(Arrays.asList(scopeAwareComponents));
    }

    /**
     * Initializes SimpleDeadlineManager with {@code scopeAwareComponents} which will load and send messages to
     * {@link org.axonframework.messaging.Scope} implementing components. {@link NoTransactionManager} is used as
     * transaction manager and {@link Executors#newSingleThreadScheduledExecutor()} is used as scheduled executor
     * service.
     *
     * @param scopeAwareComponents a {@link List} of {@link ScopeAware} components which are able to load and send
     *                             Messages to components which implement {@link org.axonframework.messaging.Scope}
     */
    public SimpleDeadlineManager(List<ScopeAware> scopeAwareComponents) {
        this(scopeAwareComponents, Executors.newSingleThreadScheduledExecutor(), NoTransactionManager.INSTANCE);
    }

    /**
     * Initializes SimpleDeadlineManager with {@code transactionManager} and {@code scopeAwareComponents} which will
     * load and send messages to {@link org.axonframework.messaging.Scope} implementing components.
     * {@link Executors#newSingleThreadScheduledExecutor()} is used as scheduled executor service.
     *
     * @param scopeAwareComponents a {@link List} of {@link ScopeAware} components which are able to load and send
     *                             Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param transactionManager   The transaction manager used to manage transaction during processing of {@link
     *                             DeadlineMessage} when deadline is not met
     */
    public SimpleDeadlineManager(List<ScopeAware> scopeAwareComponents, TransactionManager transactionManager) {
        this(scopeAwareComponents, Executors.newSingleThreadScheduledExecutor(), transactionManager);
    }

    /**
     * Initializes a SimpleDeadlineManager to handle the process around scheduling and triggering a
     * {@link DeadlineMessage}
     *
     * @param scopeAwareComponents     a {@link List} of {@link ScopeAware} components which are able to load and send
     *                                 Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param scheduledExecutorService Java's service used for scheduling and triggering deadlines
     * @param transactionManager       The transaction manager used to manage transaction during processing of {@link
     *                                 DeadlineMessage} when deadline is not met
     */
    public SimpleDeadlineManager(List<ScopeAware> scopeAwareComponents,
                                 ScheduledExecutorService scheduledExecutorService,
                                 TransactionManager transactionManager) {
        isFalse(
                scopeAwareComponents.isEmpty(),
                () -> "cannot process deadline messages without scope aware components to send them too"
        );
        notNull(scheduledExecutorService, () -> "scheduledExecutorService may not be null");
        notNull(transactionManager, () -> "transactionManager may not be null");

        this.scheduledExecutorService = scheduledExecutorService;
        this.transactionManager = transactionManager;
        this.scopeAwareComponents = scopeAwareComponents;
    }

    @Override
    public void schedule(Duration triggerDuration,
                         String deadlineName,
                         Object messageOrPayload,
                         ScopeDescriptor deadlineScope,
                         String scheduleId) {
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(
                new DeadlineTask(scheduleId, deadlineName, messageOrPayload, deadlineScope),
                triggerDuration.toMillis(),
                TimeUnit.MILLISECONDS
        );
        // TODO store deadlineName for cancelAll() and cancelSchedule()
        tokens.put(scheduleId, scheduledFuture);
    }

    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        Future<?> future = tokens.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void cancelAll(String deadlineName) {
        // TODO implement
    }

    private class DeadlineTask implements Runnable {

        private final String scheduleId;
        private final String deadlineName;
        private final Object messageOrPayload;
        private final ScopeDescriptor deadlineScope;

        private DeadlineTask(String scheduleId,
                             String deadlineName,
                             Object messageOrPayload,
                             ScopeDescriptor deadlineScope) {
            this.scheduleId = scheduleId;
            this.deadlineName = deadlineName;
            this.messageOrPayload = messageOrPayload;
            this.deadlineScope = deadlineScope;
        }

        @Override
        public void run() {
            DeadlineMessage<?> deadlineMessage =
                    GenericDeadlineMessage.asDeadlineMessage(deadlineName, messageOrPayload);
            if (logger.isDebugEnabled()) {
                logger.debug("Triggered deadline");
            }

            try {
                UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(null);
                Transaction transaction = transactionManager.startTransaction();
                unitOfWork.onCommit(u -> transaction.commit());
                unitOfWork.onRollback(u -> transaction.rollback());
                unitOfWork.execute(() -> executeScheduledDeadline(deadlineMessage, deadlineScope));
            } finally {
                //TODO Adjust remove call to take deadlineName into account
                tokens.remove(scheduleId);
            }
        }

        private void executeScheduledDeadline(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope) {
            scopeAwareComponents.stream()
                    .filter(scopeAwareComponent -> scopeAwareComponent.canResolve(deadlineScope))
                    .forEach(scopeAwareComponent -> {
                        try {
                            scopeAwareComponent.send(deadlineMessage, deadlineScope);
                        } catch (Exception e) {
                            String exceptionMessage = String.format(
                                    "Failed to send a DeadlineMessage for scope [%s]",
                                    deadlineScope.scopeDescription()
                            );
                            throw new ExecutionException(exceptionMessage, e);
                        }
                    });
        }
    }
}
