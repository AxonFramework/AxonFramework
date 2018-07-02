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

import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class SimpleDeadlineManager extends AbstractDeadlineManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleDeadlineManager.class);
    private static final String THREAD_FACTORY_GROUP_NAME = "deadlineManager";

    private final ScopeAwareProvider scopeAwareProvider;
    private final ScheduledExecutorService scheduledExecutorService;
    private final TransactionManager transactionManager;

    private final Map<DeadlineId, Future<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * Initializes SimpleDeadlineManager with {@code scopeAwareProvider} which will load and send messages to
     * {@link org.axonframework.messaging.Scope} implementing components. {@link NoTransactionManager} is used as
     * transaction manager and {@link Executors#newSingleThreadScheduledExecutor()} with {@link AxonThreadFactory} is
     * used as scheduled executor service.
     *
     * @param scopeAwareProvider a {@link List} of {@link ScopeAware} components which are able to load and send
     *                           Messages to components which implement {@link org.axonframework.messaging.Scope}
     */
    public SimpleDeadlineManager(ScopeAwareProvider scopeAwareProvider) {
        this(scopeAwareProvider, NoTransactionManager.INSTANCE);
    }

    /**
     * Initializes SimpleDeadlineManager with {@code transactionManager} and {@code scopeAwareProvider} which will
     * load and send messages to {@link org.axonframework.messaging.Scope} implementing components.
     * {@link Executors#newSingleThreadScheduledExecutor()} with {@link AxonThreadFactory} is used as scheduled executor
     * service.
     *
     * @param scopeAwareProvider a {@link List} of {@link ScopeAware} components which are able to load and send
     *                           Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param transactionManager The transaction manager used to manage transaction during processing of {@link
     *                           DeadlineMessage} when deadline is not met
     */
    public SimpleDeadlineManager(ScopeAwareProvider scopeAwareProvider, TransactionManager transactionManager) {
        this(scopeAwareProvider,
             Executors.newSingleThreadScheduledExecutor(new AxonThreadFactory(THREAD_FACTORY_GROUP_NAME)),
             transactionManager);
    }

    /**
     * Initializes a SimpleDeadlineManager to handle the process around scheduling and triggering a
     * {@link DeadlineMessage}
     *
     * @param scopeAwareProvider       a {@link List} of {@link ScopeAware} components which are able to load and send
     *                                 Messages to components which implement {@link org.axonframework.messaging.Scope}
     * @param scheduledExecutorService Java's service used for scheduling and triggering deadlines
     * @param transactionManager       The transaction manager used to manage transaction during processing of {@link
     *                                 DeadlineMessage} when deadline is not met
     */
    public SimpleDeadlineManager(ScopeAwareProvider scopeAwareProvider,
                                 ScheduledExecutorService scheduledExecutorService,
                                 TransactionManager transactionManager) {
        notNull(
                scopeAwareProvider,
                () -> "cannot process deadline messages without scope aware components to send them too"
        );
        notNull(scheduledExecutorService, () -> "scheduledExecutorService may not be null");
        notNull(transactionManager, () -> "transactionManager may not be null");

        this.scheduledExecutorService = scheduledExecutorService;
        this.transactionManager = transactionManager;
        this.scopeAwareProvider = scopeAwareProvider;
    }

    @Override
    public void schedule(Duration triggerDuration,
                         String deadlineName,
                         Object messageOrPayload,
                         ScopeDescriptor deadlineScope,
                         String scheduleId) {
        runOnPrepareCommitOrNow(() -> {
            ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(
                    new DeadlineTask(deadlineName, deadlineScope, messageOrPayload, scheduleId),
                    triggerDuration.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            scheduledTasks.put(new DeadlineId(deadlineName, scheduleId), scheduledFuture);
        });
    }

    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        runOnPrepareCommitOrNow(() -> cancelSchedule(new DeadlineId(deadlineName, scheduleId)));
    }

    @Override
    public void cancelAll(String deadlineName) {
        runOnPrepareCommitOrNow(
                () -> scheduledTasks.entrySet().stream()
                                    .map(Map.Entry::getKey)
                                    .filter(scheduledTaskId -> scheduledTaskId.getDeadlineName().equals(deadlineName))
                                    .forEach(this::cancelSchedule)
        );
    }

    private void cancelSchedule(DeadlineId deadlineId) {
        Future<?> future = scheduledTasks.remove(deadlineId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private class DeadlineTask implements Runnable {

        private final String deadlineName;
        private final ScopeDescriptor deadlineScope;
        private final Object messageOrPayload;
        private final String deadlineId;

        private DeadlineTask(String deadlineName,
                             ScopeDescriptor deadlineScope,
                             Object messageOrPayload,
                             String deadlineId) {
            this.deadlineName = deadlineName;
            this.deadlineScope = deadlineScope;
            this.messageOrPayload = messageOrPayload;
            this.deadlineId = deadlineId;
        }

        @Override
        public void run() {
            DeadlineMessage<?> deadlineMessage =
                    GenericDeadlineMessage.asDeadlineMessage(deadlineName, messageOrPayload);
            if (logger.isDebugEnabled()) {
                logger.debug("Triggered deadline");
            }

            try {
                UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(deadlineMessage);
                unitOfWork.attachTransaction(transactionManager);
                unitOfWork.execute(() -> executeScheduledDeadline(deadlineMessage, deadlineScope));
            } finally {
                scheduledTasks.remove(new DeadlineId(deadlineName, deadlineId));
            }
        }

        private void executeScheduledDeadline(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope) {
            scopeAwareProvider.provideScopeAwareStream(deadlineScope)
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

    private static class DeadlineId {

        private final String deadlineName;
        private final String deadlineId;

        private DeadlineId(String deadlineName, String deadlineId) {
            this.deadlineId = deadlineId;
            this.deadlineName = deadlineName;
        }

        public String getDeadlineName() {
            return deadlineName;
        }

        public String getDeadlineId() {
            return deadlineId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(deadlineName, deadlineId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final DeadlineId other = (DeadlineId) obj;
            return Objects.equals(this.deadlineName, other.deadlineName)
                    && Objects.equals(this.deadlineId, other.deadlineId);
        }

        @Override
        public String toString() {
            return "DeadlineId{" +
                    "deadlineName='" + deadlineName + '\'' +
                    ", deadlineId='" + deadlineId + '\'' +
                    '}';
        }
    }
}
