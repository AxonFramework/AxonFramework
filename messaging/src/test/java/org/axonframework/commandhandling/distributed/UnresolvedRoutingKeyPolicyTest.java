package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link UnresolvedRoutingKeyPolicy}.
 *
 * @author Steven van Beelen
 */
class UnresolvedRoutingKeyPolicyTest {

    private final CommandMessage<String> testCommand = new GenericCommandMessage<>("some-payload");

    @Test
    void errorStrategy() {
        assertThrows(CommandDispatchException.class, () -> UnresolvedRoutingKeyPolicy.ERROR.getRoutingKey(testCommand));
    }

    @Test
    void randomStrategy() {
        String firstResult = UnresolvedRoutingKeyPolicy.RANDOM_KEY.getRoutingKey(testCommand);
        String secondResult = UnresolvedRoutingKeyPolicy.RANDOM_KEY.getRoutingKey(testCommand);
        assertNotEquals(firstResult, secondResult);
    }

    @Test
    void staticStrategy() {
        String firstResult = UnresolvedRoutingKeyPolicy.STATIC_KEY.getRoutingKey(testCommand);
        String secondResult = UnresolvedRoutingKeyPolicy.STATIC_KEY.getRoutingKey(testCommand);
        assertEquals(firstResult, secondResult);
    }
}