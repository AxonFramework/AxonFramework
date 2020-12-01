package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link ChildForwardingCommandMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class ChildForwardingCommandMessageHandlingMemberTest {

    private MessageHandlingMember<Object> childMember;

    private ChildForwardingCommandMessageHandlingMember<Object, Object> testSubject;

    @BeforeEach
    void setUp() {
        //noinspection unchecked
        childMember = mock(MessageHandlingMember.class);

        testSubject = new ChildForwardingCommandMessageHandlingMember<>(
                Collections.emptyList(), childMember, (msg, parent) -> parent
        );
    }

    @Test
    void testCanHandleMessageTypeIsDelegatedToChildHandler() {
        when(childMember.canHandleMessageType(any())).thenReturn(true);

        assertTrue(testSubject.canHandleMessageType(CommandMessage.class));

        verify(childMember).canHandleMessageType(CommandMessage.class);
    }

    @Test
    void testIsAIsDelegatedToChildHandler() {
        when(childMember.isA("EventSourcingHandler")).thenReturn(false);

        assertFalse(testSubject.isA("EventSourcingHandler"));

        verify(childMember).isA("EventSourcingHandler");
    }

    @Test
    void testAttributesIsDelegatedToChildHandler() {
        Map<String, Object> creationPolicyAttributes = new HashMap<>();
        creationPolicyAttributes.put("creationPolicy", AggregateCreationPolicy.NEVER);
        when(childMember.attributes("CreationPolicy")).thenReturn(Optional.of(creationPolicyAttributes));

        Optional<Map<String, Object>> result = testSubject.attributes("CreationPolicy");

        assertTrue(result.isPresent());
        assertEquals(creationPolicyAttributes, result.get());

        verify(childMember).attributes("CreationPolicy");
    }
}