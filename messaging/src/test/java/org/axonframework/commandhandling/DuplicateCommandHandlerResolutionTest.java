package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandler;
import org.junit.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test towards verifying the expected behavior of the provided default {@link DuplicateCommandHandlerResolver}
 * implementations in the DuplicateCommandHandlerResolution enum.
 *
 * @author Steven van Beelen
 */
public class DuplicateCommandHandlerResolutionTest {

    private MessageHandler<? super CommandMessage<?>> initialHandler;
    private MessageHandler<? super CommandMessage<?>> duplicateHandler;

    @Before
    public void setUp() throws Exception {
        //noinspection unchecked
        initialHandler = mock(MessageHandler.class);
        doReturn(DuplicateCommandHandlerResolutionTest.class).when(initialHandler).getTargetType();
        //noinspection unchecked
        duplicateHandler = mock(MessageHandler.class);
        doReturn(DuplicateCommandHandlerResolutionTest.class).when(duplicateHandler).getTargetType();
    }

    @Test
    public void testLogAndReturnInitial() {
        DuplicateCommandHandlerResolver testSubject =
                DuplicateCommandHandlerResolution.LOG_AND_RETURN_DUPLICATE.getResolver();

        MessageHandler<? super CommandMessage<?>> result = testSubject.resolve(initialHandler, duplicateHandler);

        assertEquals(duplicateHandler, result);
    }

    @Test
    public void testLogAndReturnDuplicate() {
        DuplicateCommandHandlerResolver testSubject =
                DuplicateCommandHandlerResolution.LOG_AND_RETURN_INITIAL.getResolver();

        MessageHandler<? super CommandMessage<?>> result = testSubject.resolve(initialHandler, duplicateHandler);

        assertEquals(initialHandler, result);
    }

    @Test(expected = DuplicateCommandHandlerSubscriptionException.class)
    public void testThrow() {
        DuplicateCommandHandlerResolver testSubject =
                DuplicateCommandHandlerResolution.THROW_EXCEPTION.getResolver();

        testSubject.resolve(initialHandler, duplicateHandler);
    }
}