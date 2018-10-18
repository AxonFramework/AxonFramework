package org.axonframework.springboot;

import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
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
@EnableAutoConfiguration(exclude = {AxonServerAutoConfiguration.class})
@RunWith(SpringRunner.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class JpaEventStoreAutoConfigurationWithoutAxonServerTest {

    @Autowired
    private EventStorageEngine eventStorageEngine;

    @Autowired
    private EventStore eventStore;

    @Test
    public void testEventStore() {
        assertTrue(eventStorageEngine instanceof JpaEventStorageEngine);
        assertTrue(eventStore instanceof EmbeddedEventStore);
    }
}
