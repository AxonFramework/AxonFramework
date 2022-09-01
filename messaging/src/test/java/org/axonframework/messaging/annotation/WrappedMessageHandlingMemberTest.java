package org.axonframework.messaging.annotation;

import org.axonframework.messaging.HandlerAttributes;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link WrappedMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class WrappedMessageHandlingMemberTest {

    private MessageHandlingMember<Object> mockedHandlingMember;
    private WrappedMessageHandlingMember<Object> testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        mockedHandlingMember = mock(MessageHandlingMember.class);

        testSubject = new WrappedMessageHandlingMember<Object>(mockedHandlingMember) {
        };
    }

    @Test
    void canHandleMessageType() {
        testSubject.canHandleMessageType(QueryMessage.class);
        verify(mockedHandlingMember).canHandleMessageType(QueryMessage.class);
    }

    @Test
    void attribute() {
        testSubject.attribute(HandlerAttributes.COMMAND_ROUTING_KEY);
        verify(mockedHandlingMember).attribute(HandlerAttributes.COMMAND_ROUTING_KEY);
    }
}