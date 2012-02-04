package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.saga.SagaManager;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

/**
 * Collects operations for event handler executions.
 * 
 * @author Joris Kuipers
 *
 */
public aspect EventHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    public pointcut collectionPoint(): 
        execution(@EventHandler * *(*, ..)) || 
        (execution(void EventListener.handle(*))
         && !within(AnnotationEventListenerAdapter)
         && !within(SagaManager));

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
