package org.axonframework.insight;

import com.springsource.insight.collection.AbstractOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import org.aspectj.lang.JoinPoint;
import org.axonframework.eventhandling.EventBus;

/**
 * {@link EventBus} publish operation matching for Axon 1.x apps.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public aspect EventBus1xPublishOperationCollectionAspect extends AbstractOperationCollectionAspect {

    public pointcut collectionPoint(): execution(* org.axonframework.eventhandling.EventBus.publish(!org.axonframework.domain.EventMessage));

    @Override
    protected Operation createOperation(JoinPoint jp) {
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
