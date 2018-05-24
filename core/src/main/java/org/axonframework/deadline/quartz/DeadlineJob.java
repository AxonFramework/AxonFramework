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
import org.axonframework.deadline.DeadlineContext;
import org.axonframework.deadline.DeadlineTargetLoader;
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
 * DeadlineContext} are retrieved from the {@link JobExecutionContext}. Transaction manager and deadline target
 * loader are fetched from {@link SchedulerContext}.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class DeadlineJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadlineJob.class);

    /**
     * The key under which the {@link TransactionManager} is stored within {@link SchedulerContext}.
     */
    public static final String TRANSACTION_MANAGER_KEY = TransactionManager.class.getName();

    /**
     * The key under which the {@link DeadlineTargetLoader} is stored within {@link SchedulerContext}.
     */
    public static final String DEADLINE_TARGET_LOADER_KEY = DeadlineTargetLoader.class.getName();

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
            DeadlineContext deadlineContext = DeadlineJobDataBinder.deadlineContext(jobData);

            TransactionManager transactionManager = (TransactionManager) schedulerContext.get(TRANSACTION_MANAGER_KEY);
            DeadlineTargetLoader deadlineTargetLoader =
                    (DeadlineTargetLoader) schedulerContext.get(DEADLINE_TARGET_LOADER_KEY);


            DefaultUnitOfWork<DeadlineMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(null);
            Transaction transaction = transactionManager.startTransaction();
            unitOfWork.onCommit(u -> transaction.commit());
            unitOfWork.onRollback(u -> transaction.rollback());
            unitOfWork.execute(() -> deadlineTargetLoader.load(deadlineContext).handle(deadlineMessage));

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Job successfully executed. Deadline message [{}] processed.",
                            deadlineMessage.getPayloadType().getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.error("Exception occurred during processing a deadline job [{}]", jobDetail.getDescription(), e);
            throw new JobExecutionException(e);
        }
    }

    /**
     * This binder is used to map deadline message and deadline scope context to the job data and vice versa.
     */
    public static class DeadlineJobDataBinder {

        private static final String DEADLINE_MESSAGE_KEY = DeadlineMessage.class.getName();
        private static final String DEADLINE_CONTEXT_KEY = DeadlineContext.class.getName();

        /**
         * Converts provided {@code deadlineMessage} and {@code deadlineContext} to job data.
         *
         * @param deadlineMessage the message to be handled
         * @param deadlineContext the scope of the message
         * @return the job data
         */
        public static JobDataMap toJobData(DeadlineMessage deadlineMessage, DeadlineContext deadlineContext) {
            JobDataMap jobData = new JobDataMap();
            jobData.put(DEADLINE_MESSAGE_KEY, deadlineMessage);
            jobData.put(DEADLINE_CONTEXT_KEY, deadlineContext);
            return jobData;
        }

        /**
         * Extracts deadline message from provided {@code jobDataMap}.
         *
         * @param jobDataMap the job data
         * @return the deadline message
         */
        public static DeadlineMessage deadlineMessage(JobDataMap jobDataMap) {
            return (DeadlineMessage) jobDataMap.get(DEADLINE_MESSAGE_KEY);
        }

        /**
         * Extracts deadline scope context from provided {@code jobDataMap}.
         *
         * @param jobDataMap the job data
         * @return the deadline message
         */
        public static DeadlineContext deadlineContext(JobDataMap jobDataMap) {
            return (DeadlineContext) jobDataMap.get(DEADLINE_CONTEXT_KEY);
        }
    }
}
