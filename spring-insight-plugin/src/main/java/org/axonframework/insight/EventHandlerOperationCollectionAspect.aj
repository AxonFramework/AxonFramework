package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

public aspect EventHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    // this matches SagaManager.handle() as well: we're OK with that,
    // since async Saga invocations won't show up and this still gives 
    // a hint that the event is handled by one or more Sagas.
    public pointcut collectionPoint(): 
        execution(@EventHandler * *(*, ..)) || 
        (execution(void EventListener.handle(*))
         && !within(AnnotationEventListenerAdapter));

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
