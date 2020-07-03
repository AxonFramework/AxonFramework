/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.eventhandling;

import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link TrackingEventProcessorConfiguration}
 *
 * @author Steven van Beelen
 */
class TrackingEventProcessorConfigurationTest {

    @Test
    void testConfiguredEventTrackerStatusChangeListener() {
        Map<Integer, EventTrackerStatus> expectedTrackerStatus = Collections.emptyMap();
        EventTrackerStatusChangeListener expectedChangeListener =
                updatedTrackerStatus -> assertEquals(expectedTrackerStatus, updatedTrackerStatus);
        TrackingEventProcessorConfiguration testSubject =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing();

        testSubject.andEventTrackerStatusChangeListener(expectedChangeListener);

        EventTrackerStatusChangeListener resultChangeListener = testSubject.getEventTrackerStatusChangeListener();
        assertEquals(expectedChangeListener, resultChangeListener);
        resultChangeListener.onEventTrackerStatusChange(expectedTrackerStatus);
    }
}