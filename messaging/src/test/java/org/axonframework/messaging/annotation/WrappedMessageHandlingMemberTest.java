package org.axonframework.messaging.annotation;

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
    void testCanHandleMessageType() {
        testSubject.canHandleMessageType(QueryMessage.class);
        verify(mockedHandlingMember).canHandleMessageType(QueryMessage.class);
    }

    @Test
    void testIsA() {
        testSubject.isA("EventHandler");
        verify(mockedHandlingMember).isA("EventHandler");
    }

    @Test
    void testAttributes() {
        testSubject.attributes("CommandHandler");
        verify(mockedHandlingMember).attributes("CommandHandler");
    }

    @Test
    void testAttribute() {
        testSubject.attribute(HandlerAttributeDictionary.COMMAND_ROUTING_KEY);
        verify(mockedHandlingMember).attribute(HandlerAttributeDictionary.COMMAND_ROUTING_KEY);
    }
}