/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.actuator;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.junit.jupiter.api.*;
import org.springframework.boot.actuate.health.Health;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AxonServerHealthIndicator}.
 *
 * @author Steven van Beelen
 */
class AxonServerHealthIndicatorTest {

    @Test
    void testDoHealthCheck() {
        String testContextOne = "context-one";
        String testContextTwo = "context-two";
        String expectedDetailsContextOne = testContextOne + ".connection.active";
        String expectedDetailsContextTwo = testContextTwo + ".connection.active";

        Map<String, Boolean> testConnections = new HashMap<>();
        testConnections.put(testContextOne, true);
        testConnections.put(testContextTwo, false);

        AxonServerConnectionManager connectionManager = mock(AxonServerConnectionManager.class);
        when(connectionManager.connections()).thenReturn(testConnections);

        AxonServerHealthIndicator testSubject = new AxonServerHealthIndicator(connectionManager);

        Health.Builder healthBuilder = new Health.Builder();

        testSubject.doHealthCheck(healthBuilder);

        Health result = healthBuilder.build();
        Map<String, Object> details = result.getDetails();
        assertFalse(details.isEmpty());
        assertEquals(2, details.size());
        Object detailsContextOne = details.get(expectedDetailsContextOne);
        assertNotNull(detailsContextOne);
        assertTrue((Boolean) detailsContextOne);
        Object detailsContextTwo = details.get(expectedDetailsContextTwo);
        assertNotNull(detailsContextTwo);
        assertFalse((Boolean) detailsContextTwo);
    }
}