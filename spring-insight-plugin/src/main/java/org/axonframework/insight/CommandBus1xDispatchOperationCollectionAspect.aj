package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;

import com.springsource.insight.collection.AbstractOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

public aspect CommandBus1xDispatchOperationCollectionAspect extends AbstractOperationCollectionAspect {
    
    public pointcut collectionPoint(): execution(* CommandBus.dispatch(Object, ..));
        
    @Override
    protected Operation createOperation(JoinPoint jp) {
        Object[] args = jp.getArgs();
        Object command = args[0];
        String commandType = command.getClass().getName();
        CommandCallback<?> callback = null;
        if (args.length == 2) {
            callback = (CommandCallback<?>) args[1];
        }
        Operation op = new Operation()
            .label("Axon CommandBus Dispatch")
            .type(AxonOperationType.COMMAND_BUS)
            .sourceCodeLocation(getSourceCodeLocation(jp))
            .put("commandType", commandType);
        if (callback != null) {
            op.put("callbackType", callback.getClass().getName());
        }
        return op;
    }

}
