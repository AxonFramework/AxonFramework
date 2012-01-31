package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;

import com.springsource.insight.collection.AbstractOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

public aspect EventBus20PublishOperationCollectionAspect extends AbstractOperationCollectionAspect {
    
    public pointcut collectionPoint(): execution(* EventBus.publish(EventMessage));
        
    @Override
    protected Operation createOperation(JoinPoint jp) {
        EventMessage<?> message = (EventMessage<?>) jp.getArgs()[0];
        String eventType = message.getPayloadType().getName();
        Operation op = new Operation()
            .label("Axon EventBus Publish")
            .type(AxonOperationType.EVENT_BUS)
            .sourceCodeLocation(getSourceCodeLocation(jp))
            .put("eventType", eventType)
            .put("eventId", message.getIdentifier())
            .put("timestamp", message.getTimestamp().toString());
        Axon20OperationUtils.addMetaDataTo(op, message);
        return op;
    }

}
