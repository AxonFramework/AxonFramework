package org.axonframework.insight;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import org.aspectj.lang.JoinPoint;

/**
 * Collects operations for event handler executions.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public aspect EventHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    public pointcut collectionPoint():
            execution(@org.axonframework.eventhandling.annotation.EventHandler * *(*, ..)) ||
                    (execution(void org.axonframework.eventhandling.EventListener.handle(*))
                            && !within(org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter)
                            && !execution(void org.axonframework.saga.SagaManager.handle(*)));

    @Override
    protected Operation createOperation(JoinPoint jp) {
        Operation operation =
                super.createOperation(jp).type(AxonOperationType.EVENT_HANDLER);
        Object[] args = jp.getArgs();
        if (!AxonVersion.IS_AXON_1X) {
            if (Axon20OperationUtils.processEventMessage(args, operation)) {
                // we're done here
                return operation;
            }
        }
        Object event = args[0];
        operation.put("eventType", event.getClass().getName());
        return operation;
    }
}
