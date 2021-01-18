package org.axonframework.spring.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandMessageHandler;
import org.axonframework.spring.config.event.CommandHandlersSubscribedEvent;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.Set;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link CommandHandlerSubscriber}.
 *
 * @author Steven van Beelen
 */
class CommandHandlerSubscriberTest {

    private static final String TEST_COMMAND_NAME = "someCommand";
    private static final String OTHER_TEST_COMMAND_NAME = "someOtherCommand";

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);
    private final CommandBus commandBus = mock(CommandBus.class);
    private final TestCommandMessageHandler commandMessageHandler = new TestCommandMessageHandler();

    private CommandHandlerSubscriber testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new CommandHandlerSubscriber();
        testSubject.setApplicationContext(applicationContext);
        testSubject.setCommandBus(commandBus);
        testSubject.setCommandHandlers(Collections.singletonList(commandMessageHandler));
    }

    @Test
    void testStart() {
        testSubject.start();

        verify(commandBus).subscribe(TEST_COMMAND_NAME, commandMessageHandler);
        verify(commandBus).subscribe(OTHER_TEST_COMMAND_NAME, commandMessageHandler);
        verify(applicationContext).publishEvent(isA(CommandHandlersSubscribedEvent.class));
    }

    private static class TestCommandMessageHandler implements CommandMessageHandler {

        @Override
        public Set<String> supportedCommandNames() {
            return Sets.newSet(TEST_COMMAND_NAME, OTHER_TEST_COMMAND_NAME);
        }

        @Override
        public Object handle(CommandMessage<?> message) throws Exception {
            return null;
        }
    }
}