package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

public class CommandValidatorTest {

    private CommandValidator testSubject;

    private RecordingCommandBus commandBus;

    @Before
    public void setUp() {
        commandBus = mock(RecordingCommandBus.class);
        testSubject = new CommandValidator(commandBus, AllFieldsFilter.instance());
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

    @Test(expected = AxonAssertionError.class)
    public void testMatchWithUnexpectedNullValue() {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfOneCommandMessage(new SomeCommand(null)));

        testSubject.assertDispatchedEqualTo(new SomeCommand("test"));
    }

    private List<CommandMessage<?>> emptyCommandMessageList() {
        return Collections.emptyList();
    }

    private List<CommandMessage<?>> listOfOneCommandMessage(Object msg) {
        return Arrays.<CommandMessage<?>>asList(GenericCommandMessage.asCommandMessage(msg));
    }


    private class SomeCommand {

        private final Object value;

        public SomeCommand(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }
}