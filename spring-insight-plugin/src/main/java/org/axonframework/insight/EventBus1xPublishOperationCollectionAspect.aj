package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.eventhandling.EventBus;

import com.springsource.insight.collection.AbstractOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

public aspect EventBus1xPublishOperationCollectionAspect extends AbstractOperationCollectionAspect {
    
    public pointcut collectionPoint(): execution(* EventBus.publish(*));
        
    @Override
    protected Operation createOperation(JoinPoint jp) {
        if (!AxonVersion.IS_AXON_1X) return null;
        Object event = jp.getArgs()[0];
        String eventType = event.getClass().getName();
        Operation op = new Operation()
            .label("Axon EventBus Publish")
            .type(AxonOperationType.EVENT_BUS)
            .sourceCodeLocation(getSourceCodeLocation(jp))
            .put("eventType", eventType);
        return op;
    }

}
