package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.*;

import java.util.UUID;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MetaDataRoutingStrategyTest {

    private MetaDataRoutingStrategy testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new MetaDataRoutingStrategy("someKey");
    }

    @Test
    public void testGetRoutingKey() throws Exception {
        UUID metaDataValue = UUID.randomUUID();
        CommandMessage<Object> command = new GenericCommandMessage<Object>(new Object(),
                                                                           singletonMap("someKey", metaDataValue));
        assertEquals(metaDataValue.toString(), testSubject.getRoutingKey(command));
    }

    @Test
    public void testGetRoutingKey_NullValue() throws Exception {
        CommandMessage<Object> command = new GenericCommandMessage<Object>(new Object(), singletonMap("someKey", null));
        assertEquals(null, testSubject.getRoutingKey(command));
    }
}
