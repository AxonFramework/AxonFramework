/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.deadline;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.AxonThreadFactory;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.lifecycle.Phase;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;

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
     * Instantiate a Builder to be able to create a {@link SimpleDeadlineManager}.
     * <p>
     * The {@link ScheduledExecutorService} is defaulted to an {@link Executors#newSingleThreadScheduledExecutor()}
     * which contains an {@link AxonThreadFactory}, and the {@link TransactionManager} defaults to a
     * {@link NoTransactionManager}. The {@link ScopeAwareProvider} is a <b>hard requirement</b> and as such should be
     * provided.
     *
     * @return a Builder to be able to create a {@link SimpleDeadlineManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SimpleDeadlineManager} based on the fields contained in the {@link Builder} to handle the
     * process around scheduling and triggering a {@link DeadlineMessage}.
     * <p>
     * Will assert that the {@link ScopeAwareProvider}, {@link ScheduledExecutorService} and {@link TransactionManager}
     * are not {@code null}, and will throw an {@link AxonConfigurationException} if either of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleDeadlineManager} instance
     */
    protected SimpleDeadlineManager(Builder builder) {
        builder.validate();
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.transactionManager = builder.transactionManager;
    }

    @Override
    public String schedule(Instant triggerDateTime,
                           String deadlineName,
                           Object messageOrPayload,
                           ScopeDescriptor deadlineScope) {
        DeadlineMessage<?> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload);
        String deadlineMessageId = deadlineMessage.getIdentifier();
        DeadlineId deadlineId = new DeadlineId(deadlineName, deadlineScope, deadlineMessageId);
        runOnPrepareCommitOrNow(() -> {
            DeadlineMessage<?> interceptedDeadlineMessage = processDispatchInterceptors(deadlineMessage);
            DeadlineTask deadlineTask = new DeadlineTask(deadlineId, interceptedDeadlineMessage);
            Duration triggerDuration = Duration.between(Instant.now(), triggerDateTime);
            ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(
                    deadlineTask,
                    triggerDuration.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            scheduledTasks.put(deadlineId, scheduledFuture);
        });

        return deadlineMessageId;
    }

    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        runOnPrepareCommitOrNow(
                () -> scheduledTasks.keySet().stream()
                                    .filter(scheduledTaskId -> scheduledTaskId.getDeadlineName().equals(deadlineName)
                                            && scheduledTaskId.getDeadlineId().equals(scheduleId))
                                    .forEach(this::cancelSchedule)
        );
    }

    @Override
    public void cancelAll(String deadlineName) {
        runOnPrepareCommitOrNow(
                () -> scheduledTasks.keySet().stream()
                                    .filter(scheduledTaskId -> scheduledTaskId.getDeadlineName().equals(deadlineName))
                                    .forEach(this::cancelSchedule)
        );
    }

    @Override
    public void cancelAllWithinScope(String deadlineName, ScopeDescriptor scope) {
        runOnPrepareCommitOrNow(
                () -> scheduledTasks.keySet().stream()
                                    .filter(scheduledTaskId -> scheduledTaskId.getDeadlineName().equals(deadlineName)
                                            && scheduledTaskId.getDeadlineScope().equals(scope))
                                    .forEach(this::cancelSchedule)
        );
    }

    private void cancelSchedule(DeadlineId deadlineId) {
        Future<?> future = scheduledTasks.remove(deadlineId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Will shutdown in the {@link Phase#INBOUND_EVENT_CONNECTORS} phase.
     */
    @Override
    @ShutdownHandler(phase = Phase.INBOUND_EVENT_CONNECTORS)
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    private static class DeadlineId {

        private final String deadlineName;
        private final ScopeDescriptor deadlineScope;
        private final String deadlineId;

        private DeadlineId(String deadlineName, ScopeDescriptor deadlineScope, String deadlineId) {
            this.deadlineScope = deadlineScope;
            this.deadlineId = deadlineId;
            this.deadlineName = deadlineName;
        }

        public String getDeadlineName() {
            return deadlineName;
        }

        public ScopeDescriptor getDeadlineScope() {
            return deadlineScope;
        }

        public String getDeadlineId() {
            return deadlineId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(deadlineName, deadlineScope, deadlineId);
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
                    && Objects.equals(this.deadlineScope, other.deadlineScope)
                    && Objects.equals(this.deadlineId, other.deadlineId);
        }

        @Override
        public String toString() {
            return "DeadlineId{" +
                    "deadlineName='" + deadlineName + '\'' +
                    "deadlineScope=" + deadlineScope + '\'' +
                    ", deadlineId='" + deadlineId + '\'' +
                    '}';
        }
    }

    /**
     * Builder class to instantiate a {@link SimpleDeadlineManager}.
     * <p>
     * The {@link ScheduledExecutorService} is defaulted to an {@link Executors#newSingleThreadScheduledExecutor()}
     * which contains an {@link AxonThreadFactory}, and the {@link TransactionManager} defaults to a
     * {@link NoTransactionManager}. The {@link ScopeAwareProvider} is a <b>hard requirement</b> and as such should be
     * provided.
     */
    public static class Builder {

        private ScopeAwareProvider scopeAwareProvider;
        private ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(new AxonThreadFactory(THREAD_FACTORY_GROUP_NAME));
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;

        /**
         * Sets the {@link ScopeAwareProvider} which is capable of providing a stream of
         * {@link org.axonframework.messaging.Scope} instances for a given {@link ScopeDescriptor}. Used to return the
         * right Scope to trigger a deadline in.
         *
         * @param scopeAwareProvider a {@link ScopeAwareProvider} used to find the right
         *                           {@link org.axonframework.messaging.Scope} to trigger a deadline in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scopeAwareProvider(ScopeAwareProvider scopeAwareProvider) {
            assertNonNull(scopeAwareProvider, "ScopeAwareProvider may not be null");
            this.scopeAwareProvider = scopeAwareProvider;
            return this;
        }

        /**
         * Sets the {@link ScheduledExecutorService} used for scheduling and triggering deadlines. Defaults to a
         * {@link Executors#newSingleThreadScheduledExecutor()}, containing an {@link AxonThreadFactory}.
         *
         * @param scheduledExecutorService a {@link ScheduledExecutorService} used for scheduling and triggering
         *                                 deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            assertNonNull(scheduledExecutorService, "ScheduledExecutorService may not be null");
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them to deadline. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them to deadline
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Initializes a {@link SimpleDeadlineManager} as specified through this Builder.
         *
         * @return a {@link SimpleDeadlineManager} as specified through this Builder
         */
        public SimpleDeadlineManager build() {
            return new SimpleDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided");
        }
    }

    private class DeadlineTask implements Runnable {

        private final DeadlineId deadlineId;
        private final DeadlineMessage<?> deadlineMessage;

        private DeadlineTask(DeadlineId deadlineId,
                             DeadlineMessage<?> deadlineMessage) {
            this.deadlineMessage = deadlineMessage;
            this.deadlineId = deadlineId;
        }

        @Override
        public void run() {
            if (logger.isDebugEnabled()) {
                logger.debug("Triggered deadline");
            }

            try {
                Instant triggerInstant = GenericEventMessage.clock.instant();
                UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(new GenericDeadlineMessage<>(
                        deadlineId.getDeadlineName(),
                        deadlineMessage,
                        () -> triggerInstant));
                unitOfWork.attachTransaction(transactionManager);
                InterceptorChain chain =
                        new DefaultInterceptorChain<>(unitOfWork,
                                                      handlerInterceptors(),
                                                      deadlineMessage -> {
                                                          executeScheduledDeadline(deadlineMessage,
                                                                                   deadlineId.getDeadlineScope());
                                                          return null;
                                                      });
                ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceed);
                if (resultMessage.isExceptional()) {
                    Throwable e = resultMessage.exceptionResult();
                    logger.error("An error occurred while triggering the deadline [{}] with identifier [{}]",
                                 deadlineId.getDeadlineName(), deadlineId.getDeadlineId(), e);
                }
            } catch (Exception e) {
                logger.error("An error occurred while triggering the deadline [{}] with identifier [{}]",
                             deadlineId.getDeadlineName(), deadlineId.getDeadlineId(), e);
            } finally {
                scheduledTasks.remove(deadlineId);
            }
        }

        @SuppressWarnings("Duplicates")
        private void executeScheduledDeadline(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope) {
            scopeAwareProvider.provideScopeAwareStream(deadlineScope)
                              .filter(scopeAwareComponent -> scopeAwareComponent.canResolve(deadlineScope))
                              .forEach(scopeAwareComponent -> {
                                  try {
                                      scopeAwareComponent.send(deadlineMessage, deadlineScope);
                                  } catch (Exception e) {
                                      String exceptionMessage = format(
                                              "Failed to send a DeadlineMessage for scope [%s]",
                                              deadlineScope.scopeDescription()
                                      );
                                      throw new ExecutionException(exceptionMessage, e);
                                  }
                              });
        }
    }
}
