package org.axonframework.eventhandling.amqp;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class DefaultAMQPConsumerConfigurationTest {

    @Test
    public void testUsesDefaultSettings() {
        DefaultAMQPConsumerConfiguration testSubject = new DefaultAMQPConsumerConfiguration("QueueName");

        assertEquals("QueueName", testSubject.getQueueName());
        assertEquals(true, testSubject.getExclusive());
        assertNull(testSubject.getPrefetchCount());
    }

    @Test
    public void testNullQueueAllowed() {
        assertNull(new DefaultAMQPConsumerConfiguration(null).getQueueName());
    }

}
