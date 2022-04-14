package org.axonframework.deadline.jobrunnr;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.AbstractDeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.ExecutionException;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.ResultMessage;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;

public class JobRunrDeadlineManager extends AbstractDeadlineManager {
    
    private static final Logger LOGGER = getLogger(JobRunrDeadlineManager.class);
    
    private final ScopeAwareProvider scopeAwareProvider;
    private final JobScheduler jobScheduler;
    private final TransactionManager transactionManager;
    
    public static Builder builder() {
        return new Builder();
    }
    
    protected JobRunrDeadlineManager(Builder builder) {
        builder.validate();
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.jobScheduler = builder.jobScheduler;
        this.transactionManager = builder.transactionManager;
    }
    
    @Override
    public String schedule(Instant triggerDateTime, String deadlineName, Object messageOrPayload, ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        UUID deadlineId = UUID.fromString(deadlineMessage.getIdentifier());
        runOnPrepareCommitOrNow(() -> {
            DeadlineMessage<Object> interceptedDeadlineMessage = processDispatchInterceptors(deadlineMessage);
            DeadlineDetails deadlineDetails =
                    new DeadlineDetails(deadlineName, deadlineId, deadlineScope.scopeDescription(), interceptedDeadlineMessage.getPayload(), interceptedDeadlineMessage.getMetaData().keySet().toArray(new String[0]),
                            interceptedDeadlineMessage.getMetaData().values().toArray());
            jobScheduler.<JobRunrDeadlineManager>schedule(deadlineId, triggerDateTime, x -> x.execute(deadlineDetails));
        });
        return deadlineId.toString();
    }
    
    @Override
    public void cancelSchedule(String deadlineName, String scheduleId) {
        runOnPrepareCommitOrNow(() -> jobScheduler.delete(scheduleId));
    }
    
    @Override
    public void cancelAll(String deadlineName) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void cancelAllWithinScope(String deadlineName, ScopeDescriptor scope) {
        throw new UnsupportedOperationException();
    }
    
    public void execute(DeadlineDetails deadlineDetails) {
        try {
            Instant triggerInstant = GenericEventMessage.clock.instant();
            Map<String, Object> metaData = new HashMap<>();
            for (int i = 0; i < deadlineDetails.getKeys().length; i++) {
                metaData.put(deadlineDetails.getKeys()[i], deadlineDetails.getValues()[i]);
            }
            GenericDeadlineMessage rebuiltDeadlineMessage = new GenericDeadlineMessage<>(deadlineDetails.getDeadlineName(),
                    deadlineDetails.getDeadlineId().toString(),
                    deadlineDetails.getPayload(),
                    metaData,
                    triggerInstant);
            UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(rebuiltDeadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
            InterceptorChain chain =
                    new DefaultInterceptorChain<>(unitOfWork,
                            handlerInterceptors(),
                            deadlineMessage -> {
                                executeScheduledDeadline(deadlineMessage, (ScopeDescriptor) deadlineDetails::getScopeDescription);
                                return null;
                            });
            ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceed);
            if (resultMessage.isExceptional()) {
                Throwable e = resultMessage.exceptionResult();
                LOGGER.error("An error occurred while triggering the deadline [{}] with identifier [{}]",
                        deadlineDetails.getDeadlineName(), deadlineDetails.getDeadlineId(), e);
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while triggering the deadline [{}] with identifier [{}]",
                    deadlineDetails.getDeadlineName(), deadlineDetails.getDeadlineId(), e);
        }
    }
    
    private void executeScheduledDeadline(DeadlineMessage<?> deadlineMessage, ScopeDescriptor deadlineScope) {
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
    
    public static class Builder {
        
        private JobScheduler jobScheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private TransactionManager transactionManager;
        
        public Builder scopeAwareProvider(ScopeAwareProvider scopeAwareProvider) {
            assertNonNull(scopeAwareProvider, "ScopeAwareProvider may not be null");
            this.scopeAwareProvider = scopeAwareProvider;
            return this;
        }
        
        public Builder jobScheduler(JobScheduler jobScheduler) {
            assertNonNull(jobScheduler, "JobScheduler may not be null");
            this.jobScheduler = jobScheduler;
            return this;
        }
        
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }
        
        public JobRunrDeadlineManager build() {
            return new JobRunrDeadlineManager(this);
        }
        
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided");
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided");
            assertNonNull(transactionManager, "The TransactionManager is a hard requirement and should be provided");
        }
    }
}
