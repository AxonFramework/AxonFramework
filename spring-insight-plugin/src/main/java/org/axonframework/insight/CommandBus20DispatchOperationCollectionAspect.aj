package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;

import com.springsource.insight.collection.AbstractOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

/**
 * {@link CommandBus} dispatch operation matching for Axon 2.0 apps.
 * 
 * @author Joris Kuipers
 *
 */
public aspect CommandBus20DispatchOperationCollectionAspect extends AbstractOperationCollectionAspect {
    
    public pointcut collectionPoint(): execution(* CommandBus.dispatch(CommandMessage, ..));
        
    @Override
    protected Operation createOperation(JoinPoint jp) {
        Object[] args = jp.getArgs();
        CommandMessage<?> message = (CommandMessage<?>) args[0];
        String commandType = message.getPayloadType().getName();
        Operation op = new Operation()
            .label("Axon CommandBus Dispatch")
            .type(AxonOperationType.COMMAND_BUS)
            .sourceCodeLocation(getSourceCodeLocation(jp))
            .put("commandType", commandType)
            .put("commandId", message.getIdentifier());
        if (args.length == 2) {
            op.put("callbackType", args[1].getClass().getName());
        }
        Axon20OperationUtils.addMetaDataTo(op, message);
        return op;
    }

}
