package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;

/**
 * Collects operations for {@link Saga} executions.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public aspect SagaOperationCollectionAspect extends MethodOperationCollectionAspect {

    public pointcut collectionPoint(): 
        execution(@org.axonframework.saga.annotation.SagaEventHandler * *(*, ..)) ||
            (execution(void org.axonframework.saga.Saga.handle(*))
                    && !within(org.axonframework.saga.annotation.AbstractAnnotatedSaga));

    @Override
    protected Operation createOperation(JoinPoint jp) {
        Saga saga = (Saga) jp.getTarget();
        Operation operation = super.createOperation(jp)
                                   .type(AxonOperationType.SAGA)
                                   .put("id", saga.getSagaIdentifier());
        OperationMap values = operation.createMap("associationValues");
        for (AssociationValue value : saga.getAssociationValues()) {
            values.put(value.getKey(), value.getValue());
        }
        Object[] args = jp.getArgs();
        if (!AxonVersion.IS_AXON_1X) {
            Axon20OperationUtils.processEventMessage(args, operation);
            // we're done here
            return operation;
        }
        Object event = args[0];
        operation.put("eventType", event.getClass().getName());
        return operation;
    }
}
