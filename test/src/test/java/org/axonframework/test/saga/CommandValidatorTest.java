package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.test.AxonAssertionError;
import org.axonframework.test.matchers.AllFieldsFilter;
import org.axonframework.test.utils.RecordingCommandBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandValidatorTest {

    private CommandValidator testSubject;

    private RecordingCommandBus commandBus;

    @BeforeEach
    void setUp() {
        commandBus = mock(RecordingCommandBus.class);
        testSubject = new CommandValidator(commandBus, AllFieldsFilter.instance());
    }

    @Test
    void testAssertEmptyDispatchedEqualTo() {
        when(commandBus.getDispatchedCommands()).thenReturn(emptyCommandMessageList());

        testSubject.assertDispatchedEqualTo();
    }

    @Test
    void testAssertNonEmptyDispatchedEqualTo() {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfOneCommandMessage("command"));

        testSubject.assertDispatchedEqualTo("command");
    }

    @Test
    void testMatchWithUnexpectedNullValue() {
        when(commandBus.getDispatchedCommands()).thenReturn(listOfOneCommandMessage(new SomeCommand(null)));

        assertThrows(AxonAssertionError.class, () -> testSubject.assertDispatchedEqualTo(new SomeCommand("test")));
    }

    private List<CommandMessage<?>> emptyCommandMessageList() {
        return Collections.emptyList();
    }

    private List<CommandMessage<?>> listOfOneCommandMessage(Object msg) {
        return Collections.singletonList(GenericCommandMessage.asCommandMessage(msg));
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
