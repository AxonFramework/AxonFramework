/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.core.sequencing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MetadataSequencingPolicy}.
 *
 * @author Lucas Campos
 */
public class MetadataSequencingPolicyTest {

    @Test
    void propertyShouldReadCorrectValue() {
        final MetadataSequencingPolicy metadataPolicy = new MetadataSequencingPolicy("metadataKey");

        EventMessage testEvent = EventTestUtils.asEventMessage("42").withMetadata(Map.of("metadataKey", "metadataValue"));

        assertThat(metadataPolicy.getSequenceIdentifierFor(testEvent, new StubProcessingContext())).contains("metadataValue");
    }

    @Test
    void shouldReturnEmptyIfMetaDataDoesNotContainsTheKey() {
        final MetadataSequencingPolicy metadataPolicy = new MetadataSequencingPolicy("metadataKey");

        assertThat(metadataPolicy.getSequenceIdentifierFor(EventTestUtils.asEventMessage("42"), new StubProcessingContext())).isEmpty();
    }

    @Test
    void nullMetadataKeyShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> new MetadataSequencingPolicy(null));
    }

    @Test
    void blankMetadataKeyShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> new MetadataSequencingPolicy("   "));
    }
}
