/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.deadline.quartz;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ResultMessage;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.axonframework.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.deadlineMessage;
import static org.axonframework.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.deadlineScope;
import static org.axonframework.messaging.Headers.*;

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

    private static final Logger logger = LoggerFactory.getLogger(DeadlineJob.class);

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

    public static final String HANDLER_INTERCEPTORS = MessageHandlerInterceptor.class.getName();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (logger.isDebugEnabled()) {
            logger.debug("Starting a deadline job");
        }

        JobDetail jobDetail = context.getJobDetail();
        JobDataMap jobData = jobDetail.getJobDataMap();

        SchedulerContext schedulerContext;
        try {
            schedulerContext = context.getScheduler().getContext();
        } catch (Exception e) {
            logger.error("Exception occurred during processing a deadline job [{}]", jobDetail.getDescription(), e);
            throw new JobExecutionException(e);
        }

        Serializer serializer = (Serializer) schedulerContext.get(JOB_DATA_SERIALIZER);
        TransactionManager transactionManager = (TransactionManager) schedulerContext.get(TRANSACTION_MANAGER_KEY);
        ScopeAwareProvider scopeAwareComponents = (ScopeAwareProvider) schedulerContext.get(SCOPE_AWARE_RESOLVER);
        @SuppressWarnings("unchecked")
        List<MessageHandlerInterceptor<? super DeadlineMessage<?>>> handlerInterceptors =
                (List<MessageHandlerInterceptor<? super DeadlineMessage<?>>>)
                        schedulerContext.get(HANDLER_INTERCEPTORS);

        DeadlineMessage<?> deadlineMessage = deadlineMessage(serializer, jobData);
        ScopeDescriptor deadlineScope = deadlineScope(serializer, jobData);

        DefaultUnitOfWork<DeadlineMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(deadlineMessage);
        unitOfWork.attachTransaction(transactionManager);
        InterceptorChain chain =
                new DefaultInterceptorChain<>(unitOfWork,
                                              handlerInterceptors,
                                              interceptedDeadlineMessage -> {
                                                  executeScheduledDeadline(scopeAwareComponents,
                                                                           interceptedDeadlineMessage,
                                                                           deadlineScope);
                                                  return null;
                                              });
        ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceed);
        if (resultMessage.isExceptional()) {
            logger.error("Exception occurred during processing a deadline job [{}]",
                         jobDetail.getDescription(),
                         resultMessage.exceptionResult());
            throw new JobExecutionException(resultMessage.exceptionResult());
        } else if (logger.isInfoEnabled()) {
            logger.info("Job successfully executed. Deadline message [{}] processed.",
                        deadlineMessage.getPayloadType().getSimpleName());
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

        /**
         * Key pointing to the serialized {@link DeadlineMessage}.
         *
         * @deprecated in favor of the separate DeadlineMessage keys | only maintained for backwards compatibility
         */
        @Deprecated
        public static final String SERIALIZED_DEADLINE_MESSAGE = "serializedDeadlineMessage";
        /**
         * Key pointing to the class name of the serialized {@link DeadlineMessage}.
         *
         * @deprecated in favor of the separate DeadlineMessage keys | only maintained for backwards compatibility
         */
        @Deprecated
        public static final String SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME = "serializedDeadlineMessageClassName";
        /**
         * Key pointing to the serialized deadline {@link ScopeDescriptor} in the {@link JobDataMap}
         */
        public static final String SERIALIZED_DEADLINE_SCOPE = "serializedDeadlineScope";
        /**
         * Key pointing to the class name of the deadline {@link ScopeDescriptor} in the {@link JobDataMap}
         */
        public static final String SERIALIZED_DEADLINE_SCOPE_CLASS_NAME = "serializedDeadlineScopeClassName";

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
            putDeadlineMessage(jobData, deadlineMessage, serializer);
            putDeadlineScope(jobData, deadlineScope, serializer);
            return jobData;
        }

        private static void putDeadlineMessage(JobDataMap jobData,
                                               DeadlineMessage deadlineMessage,
                                               Serializer serializer) {
            jobData.put(DEADLINE_NAME, deadlineMessage.getDeadlineName());
            jobData.put(MESSAGE_ID, deadlineMessage.getIdentifier());
            jobData.put(MESSAGE_TIMESTAMP, deadlineMessage.getTimestamp().toEpochMilli());

            SerializedObject<byte[]> serializedDeadlinePayload =
                    serializer.serialize(deadlineMessage.getPayload(), byte[].class);
            jobData.put(SERIALIZED_MESSAGE_PAYLOAD, serializedDeadlinePayload.getData());
            jobData.put(MESSAGE_TYPE, serializedDeadlinePayload.getType().getName());
            jobData.put(MESSAGE_REVISION, serializedDeadlinePayload.getType().getRevision());

            SerializedObject<byte[]> serializedDeadlineMetaData =
                    serializer.serialize(deadlineMessage.getMetaData(), byte[].class);
            jobData.put(MESSAGE_METADATA, serializedDeadlineMetaData.getData());
        }

        private static void putDeadlineScope(JobDataMap jobData, ScopeDescriptor deadlineScope, Serializer serializer) {
            SerializedObject<byte[]> serializedDeadlineScope = serializer.serialize(deadlineScope, byte[].class);
            jobData.put(SERIALIZED_DEADLINE_SCOPE, serializedDeadlineScope.getData());
            jobData.put(SERIALIZED_DEADLINE_SCOPE_CLASS_NAME, serializedDeadlineScope.getType().getName());
        }

        /**
         * Extracts a {@link DeadlineMessage} from provided {@code jobDataMap}.
         *
         * <b>Note</b> that this function is able to retrieve two different formats of DeadlineMessage. The first being
         * a now deprecated solution which used to serialized the entire {@link DeadlineMessage} into the
         * {@link JobDataMap}. This is only kept for backwards compatibility. The second is the new solution which
         * stores all the required deadline fields separately, only de-/serializing the payload and metadata of a
         * DeadlineMessage instead of the entire message.
         *
         * @param serializer the {@link Serializer} used to deserialize the contents of the given {@code} jobDataMap}
         *                   into a {@link DeadlineMessage}
         * @param jobDataMap the {@link JobDataMap} which should contain a {@link DeadlineMessage}
         * @return the {@link DeadlineMessage} pulled from the {@code jobDataMap}
         */
        public static DeadlineMessage deadlineMessage(Serializer serializer, JobDataMap jobDataMap) {
            if (jobDataMap.containsKey(SERIALIZED_DEADLINE_MESSAGE)) {
                SimpleSerializedObject<byte[]> serializedDeadlineMessage = new SimpleSerializedObject<>(
                        (byte[]) jobDataMap.get(SERIALIZED_DEADLINE_MESSAGE), byte[].class,
                        (String) jobDataMap.get(SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME), null
                );
                return serializer.deserialize(serializedDeadlineMessage);
            }

            return new GenericDeadlineMessage<>((String) jobDataMap.get(DEADLINE_NAME),
                                                (String) jobDataMap.get(MESSAGE_ID),
                                                deserializeDeadlinePayload(serializer, jobDataMap),
                                                deserializeDeadlineMetaData(serializer, jobDataMap),
                                                retrieveDeadlineTimestamp(jobDataMap));
        }

        private static Object deserializeDeadlinePayload(Serializer serializer, JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedDeadlinePayload = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(SERIALIZED_MESSAGE_PAYLOAD),
                    byte[].class,
                    (String) jobDataMap.get(MESSAGE_TYPE),
                    (String) jobDataMap.get(MESSAGE_REVISION)
            );
            return serializer.deserialize(serializedDeadlinePayload);
        }

        private static Map<String, ?> deserializeDeadlineMetaData(Serializer serializer, JobDataMap jobDataMap) {
            SimpleSerializedObject<byte[]> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                    (byte[]) jobDataMap.get(MESSAGE_METADATA), byte[].class, MetaData.class.getName(), null
            );
            return serializer.deserialize(serializedDeadlineMetaData);
        }

        private static Instant retrieveDeadlineTimestamp(JobDataMap jobDataMap) {
            return Instant.ofEpochMilli((long) jobDataMap.get(MESSAGE_TIMESTAMP));
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
