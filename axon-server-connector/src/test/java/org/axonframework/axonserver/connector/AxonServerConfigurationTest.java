package org.axonframework.axonserver.connector;

import org.junit.jupiter.api.Test;

import static org.axonframework.axonserver.connector.AxonServerConfiguration.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test Axon Server Configuration
 * Testing falling back to defaults for Flow Control per message type
 *
 */
class AxonServerConfigurationTest {


    @Test
    void eventsFlowControl() {

        AxonServerConfiguration axonServerConfiguration = builder().eventFlowControl(10, 20, 30).build();

        assertEquals(10, axonServerConfiguration.getEventFlowControl().getInitialNrOfPermits());
        assertEquals(20, axonServerConfiguration.getEventFlowControl().getNrOfNewPermits());
        assertEquals(30, axonServerConfiguration.getEventFlowControl().getNewPermitsThreshold());
        assertEquals(1000, axonServerConfiguration.getInitialNrOfPermits());
        assertEquals(500, axonServerConfiguration.getNrOfNewPermits());
        assertEquals(500, axonServerConfiguration.getNewPermitsThreshold());

    }

    @Test
    void commandFlowControl() {

        AxonServerConfiguration axonServerConfiguration = builder().commandFlowControl(10, 20, 30).build();

        assertEquals(10, axonServerConfiguration.getCommandFlowControl().getInitialNrOfPermits());
        assertEquals(20, axonServerConfiguration.getCommandFlowControl().getNrOfNewPermits());
        assertEquals(30, axonServerConfiguration.getCommandFlowControl().getNewPermitsThreshold());
        assertEquals(1000, axonServerConfiguration.getInitialNrOfPermits());
        assertEquals(500, axonServerConfiguration.getNrOfNewPermits());
        assertEquals(500, axonServerConfiguration.getNewPermitsThreshold());

    }

    @Test
    void queryFlowControl() {

        AxonServerConfiguration axonServerConfiguration = builder().queryFlowControl(10, 20, 30).build();

        assertEquals(10, axonServerConfiguration.getQueryFlowControl().getInitialNrOfPermits());
        assertEquals(20, axonServerConfiguration.getQueryFlowControl().getNrOfNewPermits());
        assertEquals(30, axonServerConfiguration.getQueryFlowControl().getNewPermitsThreshold());
        assertEquals(1000, axonServerConfiguration.getInitialNrOfPermits());
        assertEquals(500, axonServerConfiguration.getNrOfNewPermits());
        assertEquals(500, axonServerConfiguration.getNewPermitsThreshold());

    }

}