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

package org.axonframework.deadline.quartz;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quartz job which depicts handling of a scheduled deadline message. The {@link DeadlineMessage} and {@link
 * ScopeDescriptor} are retrieved from the {@link JobExecutionContext}. The {@link TransactionManager} and
 * {@link ScopeAware} components are fetched from {@link SchedulerContext}.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.3
 */
public class DeadlineJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineJob.class);

    /**
     * The key under which the {@link TransactionManager} is stored within {@link SchedulerContext}.
     */
    public static final String TRANSACTION_MANAGER_KEY = TransactionManager.class.getName();

    /**
     * The key under which the {@link ScopeAwareProvider} is stored within {@link SchedulerContext}.
     */
    public static final String SCOPE_AWARE_RESOLVER = ScopeAwareProvider.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting a deadline job");
        }

        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();

        try {
            SchedulerContext schedulerContext = context.getScheduler().getContext();
            DeadlineMessage deadlineMessage = DeadlineJobDataBinder.deadlineMessage(jobData);
            ScopeDescriptor deadlineScope = DeadlineJobDataBinder.deadlineScope(jobData);

            TransactionManager transactionManager = (TransactionManager) schedulerContext.get(TRANSACTION_MANAGER_KEY);
            ScopeAwareProvider scopeAwareComponents =
                    (ScopeAwareProvider) schedulerContext.get(SCOPE_AWARE_RESOLVER);


            DefaultUnitOfWork<DeadlineMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
            Transaction transaction = transactionManager.startTransaction();
            unitOfWork.onCommit(u -> transaction.commit());
            unitOfWork.onRollback(u -> transaction.rollback());
            unitOfWork.execute(() -> executeScheduledDeadline(scopeAwareComponents, deadlineMessage, deadlineScope));

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Job successfully executed. Deadline message [{}] processed.",
                            deadlineMessage.getPayloadType().getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.error("Exception occurred during processing a deadline job [{}]", jobDetail.getDescription(), e);
            throw new JobExecutionException(e);
        }
    }

    private void executeScheduledDeadline(ScopeAwareProvider scopeAwareComponents,
                                          DeadlineMessage deadlineMessage,
                                          ScopeDescriptor deadlineScope) {
        scopeAwareComponents.provideScopeAwareStream(deadlineScope)
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

    /**
     * This binder is used to map deadline message and deadline scopes to the job data and vice versa.
     */
    public static class DeadlineJobDataBinder {

        private static final String DEADLINE_MESSAGE_KEY = DeadlineMessage.class.getName();
        private static final String DEADLINE_SCOPE_KEY = ScopeDescriptor.class.getName();

        /**
         * Converts provided {@code deadlineMessage} and {@code deadlineScope} to a {@link JobDataMap}.
         *
         * @param deadlineMessage the {@link DeadlineMessage} to be handled
         * @param deadlineScope   the {@link ScopeDescriptor} of the {@link org.axonframework.messaging.Scope} the
         *                        {@code deadlineMessage} should go to.
         * @return a {@link JobDataMap} containing the {@code deadlineMessage} and {@code deadlineScope}
         */
        public static JobDataMap toJobData(DeadlineMessage deadlineMessage, ScopeDescriptor deadlineScope) {
            JobDataMap jobData = new JobDataMap();
            jobData.put(DEADLINE_MESSAGE_KEY, deadlineMessage);
            jobData.put(DEADLINE_SCOPE_KEY, deadlineScope);
            return jobData;
        }

        /**
         * Extracts a {@link DeadlineMessage} from provided {@code jobDataMap}.
         *
         * @param jobDataMap the {@link JobDataMap} which should contain a {@link DeadlineMessage}
         * @return the {@link DeadlineMessage} pulled from the {@code jobDataMap}
         */
        public static DeadlineMessage deadlineMessage(JobDataMap jobDataMap) {
            return (DeadlineMessage) jobDataMap.get(DEADLINE_MESSAGE_KEY);
        }

        /**
         * Extracts a {@link ScopeDescriptor} describing the deadline {@link org.axonframework.messaging.Scope}, pulled
         * from provided {@code jobDataMap}.
         *
         * @param jobDataMap the {@link JobDataMap} which should contain a {@link ScopeDescriptor}
         * @return the {@link ScopeDescriptor} describing the deadline {@link org.axonframework.messaging.Scope}, pulled
         * from provided {@code jobDataMap}
         */
        public static ScopeDescriptor deadlineScope(JobDataMap jobDataMap) {
            return (ScopeDescriptor) jobDataMap.get(DEADLINE_SCOPE_KEY);
        }
    }
}
