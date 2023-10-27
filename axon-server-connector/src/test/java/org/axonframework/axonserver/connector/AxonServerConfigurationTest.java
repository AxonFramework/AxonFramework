/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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