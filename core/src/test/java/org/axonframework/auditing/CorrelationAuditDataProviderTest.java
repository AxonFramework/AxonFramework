package org.axonframework.auditing;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.*;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class CorrelationAuditDataProviderTest {

    @Test
    public void testDefaultName() throws Exception {
        CommandMessage<?> command = new GenericCommandMessage<Object>("Mock");
        Map<String, Object> actual = new CorrelationAuditDataProvider().provideAuditDataFor(command);

        assertEquals(1, actual.size());
        assertEquals(command.getIdentifier(), actual.get("command-identifier"));
    }

    @Test
    public void testCustomName() throws Exception {
        CommandMessage<?> command = new GenericCommandMessage<Object>("Mock");
        Map<String, Object> actual = new CorrelationAuditDataProvider("bla").provideAuditDataFor(command);

        assertEquals(1, actual.size());
        assertEquals(command.getIdentifier(), actual.get("bla"));
    }
}
