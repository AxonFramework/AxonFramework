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

import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaMessageSource}.
 *
 * @author Nakul Mishra
 */
public class KafkaMessageSourceTests {

    @Test(expected = IllegalArgumentException.class)
    public void testCreatingKafkaMessageSourceUsingInvalidFetcher() {
        new KafkaMessageSource<>(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOpenMessageStreamWithInvalidTrackingToken() {
        KafkaMessageSource<String, byte[]> messageSource = new KafkaMessageSource<>(fetcher());
        messageSource.openStream(new TrackingToken() {});
    }

    @Test
    public void testOpenMessageStreamWithValidToken() {
        throw new UnsupportedOperationException("test is not implemented yet");
    }

    @SuppressWarnings("unchecked")
    private Fetcher<String, byte[]> fetcher() {
        return (Fetcher<String, byte[]>) mock(Fetcher.class);
    }
}