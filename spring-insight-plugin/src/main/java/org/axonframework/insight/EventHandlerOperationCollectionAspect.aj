package org.axonframework.insight;

import java.util.Map.Entry;

import org.aspectj.lang.JoinPoint;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.AnnotationEventListenerAdapter;
import org.axonframework.eventhandling.annotation.EventHandler;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;
import com.springsource.insight.intercept.operation.OperationType;

public aspect EventHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    private static final OperationType TYPE = OperationType.valueOf("event_handler_operation");
    
    public pointcut collectionPoint(): 
        execution(@EventHandler * *(*, ..)) || 
        (execution(void EventListener.handle(EventMessage))
         && !within(AnnotationEventListenerAdapter));


    @Override
    protected Operation createOperation(JoinPoint jp) {
        Object[] args = jp.getArgs();
        EventMessage<?> eventMessage = null;
        Object event = args[0];
        if (args[0] instanceof EventMessage<?>) {
            eventMessage = (EventMessage<?>) args[0];
            event = eventMessage.getPayload();
        } else {
            event = args[0];
        }
        Operation operation = super.createOperation(jp)
            .type(TYPE)
            .put("eventType", event.getClass().getName());
        if (eventMessage != null) {
        	OperationMap map = operation.createMap("metaData");
        	for (Entry<String, Object> entry: eventMessage.getMetaData().entrySet()) {
        		map.put(entry.getKey(), entry.getValue().toString());
        	}
        }
        // TODO: what else do want to add to the operation?
        return operation;
    }

}
