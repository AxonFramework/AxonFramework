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

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.axonframework.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.deadlineMessage;
import static org.axonframework.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.deadlineScope;

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
     * The key under which the {@link TransactionManager} is stored within the {@link SchedulerContext}.
     */
    public static final String TRANSACTION_MANAGER_KEY = TransactionManager.class.getName();

    /**
     * The key under which the {@link ScopeAwareProvider} is stored within the {@link SchedulerContext}.
     */
    public static final String SCOPE_AWARE_RESOLVER = ScopeAwareProvider.class.getName();

    /**
     * The key under which the {@link Serializer} is stored within the {@link SchedulerContext}.
     */
    public static final String JOB_DATA_SERIALIZER = Serializer.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting a deadline job");
        }

        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();

        try {
            SchedulerContext schedulerContext = context.getScheduler().getContext();

            Serializer serializer = (Serializer) schedulerContext.get(JOB_DATA_SERIALIZER);
            TransactionManager transactionManager = (TransactionManager) schedulerContext.get(TRANSACTION_MANAGER_KEY);
            ScopeAwareProvider scopeAwareComponents = (ScopeAwareProvider) schedulerContext.get(SCOPE_AWARE_RESOLVER);

            DeadlineMessage<?> deadlineMessage = deadlineMessage(serializer, jobData);
            ScopeDescriptor deadlineScope = deadlineScope(serializer, jobData);

            DefaultUnitOfWork<DeadlineMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(deadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
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

    /**
     * This binder is used to map deadline message and deadline scopes to the job data and vice versa.
     */
    public static class DeadlineJobDataBinder {

        private static final String SERIALIZED_DEADLINE_MESSAGE = "serializedDeadlineMessage";
        private static final String SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME = "serializedDeadlineMessageClassName";
        private static final String SERIALIZED_DEADLINE_SCOPE = "serializedDeadlineScope";
        private static final String SERIALIZED_DEADLINE_SCOPE_CLASS_NAME = "serializedDeadlineScopeClassName";

        /**
         * Serializes the provided {@code deadlineMessage} and {@code deadlineScope} and puts them in a {@link
         * JobDataMap}.
         *
         * @param serializer      the {@link Serializer} used to serialize the given {@code deadlineMessage} and {@code
         *                        deadlineScope}
         * @param deadlineMessage the {@link DeadlineMessage} to be handled
         * @param deadlineScope   the {@link ScopeDescriptor} of the {@link org.axonframework.messaging.Scope} the
         *                        {@code deadlineMessage} should go to.
         * @return a {@link JobDataMap} containing the {@code deadlineMessage} and {@code deadlineScope}
         */
        public static JobDataMap toJobData(Serializer serializer,
                                           DeadlineMessage deadlineMessage,
                                           ScopeDescriptor deadlineScope) {
            JobDataMap jobData = new JobDataMap();

            SerializedObject<byte[]> serializedDeadlineMessage = serializer.serialize(deadlineMessage, byte[].class);
            jobData.put(SERIALIZED_DEADLINE_MESSAGE, serializedDeadlineMessage.getData());
            jobData.put(SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME, serializedDeadlineMessage.getType().getName());

            SerializedObject<byte[]> serializedDeadlineScope = serializer.serialize(deadlineScope, byte[].class);
            jobData.put(SERIALIZED_DEADLINE_SCOPE, serializedDeadlineScope.getData());
            jobData.put(SERIALIZED_DEADLINE_SCOPE_CLASS_NAME, serializedDeadlineScope.getType().getName());

            return jobData;
        }

        /**
         * Extracts a {@link DeadlineMessage} from provided {@code jobDataMap}.
         *
         * @param serializer the {@link Serializer} used to deserialize the contents of the given {@code} jobDataMap}
         *                   into a {@link DeadlineMessage}
         * @param jobDataMap the {@link JobDataMap} which should contain a {@link DeadlineMessage}
         * @return the {@link DeadlineMessage} pulled from the {@code jobDataMap}
         */
        public static DeadlineMessage deadlineMessage(Serializer serializer, JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedDeadlineMessage = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(SERIALIZED_DEADLINE_MESSAGE), byte[].class,
                    (String) jobDataMap.get(SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME), null
            );
            return serializer.deserialize(serializedDeadlineMessage);
        }

        /**
         * Extracts a {@link ScopeDescriptor} describing the deadline {@link org.axonframework.messaging.Scope}, pulled
         * from provided {@code jobDataMap}.
         *
         * @param serializer the {@link Serializer} used to deserialize the contents of the given {@code} jobDataMap}
         *                   into a {@link ScopeDescriptor}
         * @param jobDataMap the {@link JobDataMap} which should contain a {@link ScopeDescriptor}
         * @return the {@link ScopeDescriptor} describing the deadline {@link org.axonframework.messaging.Scope}, pulled
         * from provided {@code jobDataMap}
         */
        public static ScopeDescriptor deadlineScope(Serializer serializer, JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedDeadlineScope = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(SERIALIZED_DEADLINE_SCOPE), byte[].class,
                    (String) jobDataMap.get(SERIALIZED_DEADLINE_SCOPE_CLASS_NAME), null
            );
            return serializer.deserialize(serializedDeadlineScope);
        }
    }
}
