/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Context;
import org.axonframework.common.SimpleContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@code static} methods from the {@link TrackingToken} interface.
 *
 * @author Steven van Beelen
 */
class TrackingTokenTest {

    @Test
    void addToContextAddsTheGivenTrackingTokenToTheGivenContext() {
        Context testContext = new SimpleContext();
        TestToken testToken = new TestToken();

        TrackingToken.addToContext(testContext, testToken);

        assertTrue(testContext.containsResource(TrackingToken.RESOURCE_KEY));
    }

    @Test
    void fromContextReturnsAnEmptyOptionalWhenNoTokenIsPresent() {
        Context testContext = new SimpleContext();

        Optional<TrackingToken> result = TrackingToken.fromContext(testContext);

        assertTrue(result.isEmpty());
    }

    @Test
    void fromContextReturnsAnOptionalWithTheContainedToken() {
        Context testContext = new SimpleContext();
        TestToken testToken = new TestToken();

        TrackingToken.addToContext(testContext, testToken);

        Optional<TrackingToken> result = TrackingToken.fromContext(testContext);

        assertFalse(result.isEmpty());
        assertEquals(testToken, result.get());
    }

    private static class TestToken implements TrackingToken {

        @Override
        public TrackingToken lowerBound(TrackingToken other) {
            throw new UnsupportedOperationException("Not needed for testing");
        }

        @Override
        public TrackingToken upperBound(TrackingToken other) {
            throw new UnsupportedOperationException("Not needed for testing");
        }

        @Override
        public boolean covers(TrackingToken other) {
            throw new UnsupportedOperationException("Not needed for testing");
        }
    }
}