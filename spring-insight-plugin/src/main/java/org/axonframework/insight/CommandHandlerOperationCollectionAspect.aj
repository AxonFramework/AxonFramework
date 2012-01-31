package org.axonframework.insight;

import org.aspectj.lang.JoinPoint;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.unitofwork.UnitOfWork;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;

/**
 * Collects operations for command handler executions.
 * 
 * @author Joris Kuipers
 *
 */
public aspect CommandHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    public pointcut collectionPoint(): 
        execution(@CommandHandler * *(*, ..)) || 
        (execution(* org.axonframework.commandhandling.CommandHandler.handle(*, UnitOfWork))
         && !within(AnnotationCommandHandlerAdapter));

    @Override
    protected Operation createOperation(JoinPoint jp) {
        Operation operation = 
            super.createOperation(jp).type(AxonOperationType.COMMAND_HANDLER);
        Object[] args = jp.getArgs();
        if (!AxonVersion.IS_AXON_1X) {
            if (Axon20OperationUtils.processCommandMessage(args, operation)) {
                // we're done here
                return operation;
            }
        }
        Object command = args[0];
        operation.put("commandType", command.getClass().getName());
        return operation;
    }

}
