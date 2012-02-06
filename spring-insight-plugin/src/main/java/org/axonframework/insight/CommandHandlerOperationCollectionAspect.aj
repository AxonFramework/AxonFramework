package org.axonframework.insight;

import com.springsource.insight.collection.method.MethodOperationCollectionAspect;
import com.springsource.insight.intercept.operation.Operation;
import org.aspectj.lang.JoinPoint;

/**
 * Collects operations for command handler executions.
 *
 * @author Joris Kuipers
 * @since 2.0
 */
public aspect CommandHandlerOperationCollectionAspect extends MethodOperationCollectionAspect {

    public pointcut collectionPoint():
            execution(@org.axonframework.commandhandling.annotation.CommandHandler * *(*, ..)) ||
                    (execution(* org.axonframework.commandhandling.CommandHandler.handle(*, org.axonframework.unitofwork.UnitOfWork))
                            && !within(org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter));

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
