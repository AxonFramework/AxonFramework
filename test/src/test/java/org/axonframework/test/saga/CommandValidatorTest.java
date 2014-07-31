package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.test.utils.RecordingCommandBus;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommandValidatorTest {

    private CommandValidator testSubject;

    private RecordingCommandBus commandBus;

    @Before
    public void setUp(){
        commandBus = mock(RecordingCommandBus.class);
        testSubject = new CommandValidator(commandBus);
    }

    @Test
    public void testAssertEmptyDispatchedEqualTo() throws Exception {
        when(commandBus.getDispatchedCommands()).thenReturn(emptyCommandMessageList());

        testSubject.assertDispatchedEqualTo();
    }

    @Test
    public void testAssertNonEmptyDispatchedEqualTo() throws Exception {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfOneCommandMessage("command"));

        testSubject.assertDispatchedEqualTo("command");
    }

    private List<CommandMessage<?>> emptyCommandMessageList() {
        return Collections.<CommandMessage<?>>emptyList();
    }

    private List<CommandMessage<?>> listOfOneCommandMessage(String msg) {
        return Arrays.<CommandMessage<?>>asList(GenericCommandMessage.asCommandMessage(msg));
    }


}