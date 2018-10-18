package org.axonframework.springboot;

import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

/**
 * Tests JPA EventStore auto-configuration
 *
 * @author Sara Pellegrini
 */

@ContextConfiguration
@EnableAutoConfiguration
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class JpaEventStoreAutoConfigurationWithAxonServerTest {

    @Autowired
    private EventStore eventStore;

    @Test
    public void testEventStore() {
        assertTrue(eventStore instanceof AxonServerEventStore);
    }
}
