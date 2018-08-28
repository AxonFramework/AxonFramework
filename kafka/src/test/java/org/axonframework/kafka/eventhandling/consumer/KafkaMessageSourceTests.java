/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.kafka.eventhandling.consumer;

import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.junit.*;

import static org.axonframework.kafka.eventhandling.consumer.KafkaTrackingToken.emptyToken;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaMessageSource}.
 *
 * @author Nakul Mishra
 */
public class KafkaMessageSourceTests {

    @Test(expected = IllegalArgumentException.class)
    public void testCreatingMessageSource_UsingInvalidFetcher_ShouldThrowException() {
        new KafkaMessageSource(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOpeningMessageStream_WithInvalidTypeOfTrackingToken_ShouldThrowException() {
        KafkaMessageSource testSubject = new KafkaMessageSource(fetcher());
        testSubject.openStream(incompatibleTokenType());
    }

    @Test
    public void testOpeningMessageStream_WithNullToken_ShouldInvokeFetcher() {
        Fetcher fetcher = fetcher();
        KafkaMessageSource testSubject = new KafkaMessageSource(fetcher);
        testSubject.openStream(null);

        verify(fetcher, times(1)).start(any());
    }

    @Test
    public void testOpeningMessageStream_WithValidToken_ShouldStartTheFetcher() {
        Fetcher fetcher = fetcher();
        KafkaMessageSource testSubject = new KafkaMessageSource(fetcher);
        testSubject.openStream(emptyToken());

        verify(fetcher, times(1)).start(any());
    }

    private static TrackingToken incompatibleTokenType() {
        return new TrackingToken() {
            @Override
            public TrackingToken lowerBound(TrackingToken other) {
                return null;
            }

            @Override
            public TrackingToken upperBound(TrackingToken other) {
                return null;
            }

            @Override
            public boolean covers(TrackingToken other) {
                return false;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Fetcher fetcher() {
        return mock(Fetcher.class);
    }
}
