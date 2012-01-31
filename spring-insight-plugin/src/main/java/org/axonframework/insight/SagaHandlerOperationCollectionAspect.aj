package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;

public aspect SagaHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {
    
    public pointcut collectionPoint(): execution(void Saga.handle(EventMessage));

    @Override
    protected Operation createOperation(JoinPoint jp) {
        EventMessage<?> message = (EventMessage<?>) jp.getArgs()[0];
        Saga saga = (Saga) jp.getTarget();
        Operation operation = super.createOperation(jp)
            .type(AxonOperationType.SAGA_HANDLER)
            .put("eventType", message.getPayloadType().getName())
            .put("id", saga.getSagaIdentifier());
        OperationMap values = operation.createMap("associationValues");
        for (AssociationValue value : saga.getAssociationValues()) {
            values.put(value.getKey(), value.getValue());
        }
        return operation;
    }
}
