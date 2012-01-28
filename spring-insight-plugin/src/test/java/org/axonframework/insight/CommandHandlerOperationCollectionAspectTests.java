package org.axonframework.insight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.Test;

import com.springsource.insight.collection.OperationCollectionAspectSupport;
import com.springsource.insight.collection.OperationCollectionAspectTestSupport;
import com.springsource.insight.intercept.operation.Operation;
import com.springsource.insight.intercept.operation.OperationMap;

public class CommandHandlerOperationCollectionAspectTests extends OperationCollectionAspectTestSupport {
    
    @Test
    public void annotatedCommandHandlerOperationCollected() {
        new TestCommandHandler().handleCommand(new TestCommand());
        
        Operation op = getLastEntered(Operation.class);

        assertEquals("org.axonframework.insight.CommandHandlerOperationCollectionAspectTests$TestCommand", op.get("commandType"));
        assertEquals("handleCommand", op.getSourceCodeLocation().getMethodName());
    }
    
    @Test
    public void commandHandlerOperationCollected() throws Throwable {
        new TestCommandHandler().handle(
        		new GenericCommandMessage<CommandHandlerOperationCollectionAspectTests.TestCommand>(
        				new TestCommand(), Collections.singletonMap("someKey", (Object) "someValue")), 
        		new DefaultUnitOfWork());
        
        Operation op = getLastEntered(Operation.class);

        assertEquals("org.axonframework.insight.CommandHandlerOperationCollectionAspectTests$TestCommand", op.get("commandType"));
        assertEquals("handle", op.getSourceCodeLocation().getMethodName());
        OperationMap map = op.get("metaData", OperationMap.class);
        assertNotNull("CommandMessage metadata missing in operation", map);
        assertEquals(1, map.size());
        assertEquals("someValue", map.get("someKey"));
    }

    @Override
    public OperationCollectionAspectSupport getAspect() {
        return CommandHandlerOperationCollectionAspect.aspectOf();
    }
    
    static class TestCommand {}
    
    static class TestCommandHandler implements org.axonframework.commandhandling.CommandHandler<TestCommand> {
        @CommandHandler
        void handleCommand(TestCommand Command) {}

        public Object handle(CommandMessage<TestCommand> commandMessage, UnitOfWork unitOfWork) throws Throwable {
            return null;
        }
    }

}
