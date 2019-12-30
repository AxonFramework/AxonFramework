package org.axonframework.springboot;

import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JPA EventStore auto-configuration
 *
 * @author Sara Pellegrini
 */

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {AxonServerAutoConfiguration.class})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JpaEventStoreAutoConfigurationWithoutAxonServerTest {

    @Autowired
    private EventStorageEngine eventStorageEngine;

    @Autowired
    private EventStore eventStore;

    @Test
    void testEventStore() {
        assertTrue(eventStorageEngine instanceof JpaEventStorageEngine);
        assertTrue(eventStore instanceof EmbeddedEventStore);
    }
}
