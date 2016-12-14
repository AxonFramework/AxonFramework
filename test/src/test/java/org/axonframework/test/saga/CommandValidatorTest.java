package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.test.utils.RecordingCommandBus;
import org.junit.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class CommandValidatorTest {

    private CommandValidator testSubject;

    private RecordingCommandBus commandBus;

    @Before
    public void setUp() {
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

    @Test(expected = AssertionError.class)
    public void testMatchWithUnexpectedNullValue() {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfOneCommandMessage(new SomeCommand(null)));

        testSubject.assertDispatchedEqualTo(new SomeCommand("test"));
    }

    @Test
    public void testAssertTwoDispatchedEqualToIgnoreSequence() throws Exception {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfMultipleCommandMessage(new SomeCommand("command 1"), new SomeCommand("command 2")));

        testSubject.assertDispatchedEqualToIgnoringSequence(new SomeCommand("command 2"), new SomeCommand("command 1"));
    }

    private List<CommandMessage<?>> emptyCommandMessageList() {
        return Collections.emptyList();
    }

    private List<CommandMessage<?>> listOfOneCommandMessage(Object msg) {
        return Collections.singletonList(GenericCommandMessage.asCommandMessage(msg));
    }

    private List<CommandMessage<?>> listOfMultipleCommandMessage(Object... msg) {
        return Arrays.stream(msg).map(GenericCommandMessage::asCommandMessage).collect(Collectors.toList());
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
