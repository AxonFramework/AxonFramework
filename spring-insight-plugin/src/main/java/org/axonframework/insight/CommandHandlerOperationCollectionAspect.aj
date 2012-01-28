package org.axonframework.insight;

import java.util.Map.Entry;

import org.aspectj.lang.JoinPoint;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.unitofwork.UnitOfWork;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;
import com.springsource.insight.intercept.operation.OperationType;

public aspect CommandHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    private static final OperationType TYPE = OperationType.valueOf("command_handler_operation");
    
    public pointcut collectionPoint(): 
        execution(@CommandHandler * *(*, ..)) || 
        (execution(* org.axonframework.commandhandling.CommandHandler.handle(CommandMessage, UnitOfWork))
         && !within(AnnotationCommandHandlerAdapter));


    @Override
    protected Operation createOperation(JoinPoint jp) {
        Object[] args = jp.getArgs();
        CommandMessage<?> commandMessage = null;
        Object command = args[0];
        if (args[0] instanceof CommandMessage<?>) {
            commandMessage = (CommandMessage<?>) args[0];
            command = commandMessage.getPayload();
        } else {
            command = args[0];
        }
        Operation operation = super.createOperation(jp)
            .type(TYPE)
            .put("commandType", command.getClass().getName());
        if (commandMessage != null) {
            OperationMap map = operation.createMap("metaData");
            for (Entry<String, Object> entry: commandMessage.getMetaData().entrySet()) {
                map.put(entry.getKey(), entry.getValue().toString());
            }
        }
        // TODO: what else do want to add to the operation?
        return operation;
    }

}
