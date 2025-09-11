/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.sequencing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MetadataSequencingPolicy}.
 *
 * @author Lucas Campos
 */
@DisplayName("Unit-Test for the MetadataSequencingPolicy")
public class MetadataSequencingPolicyTest {

    @Test
    void propertyShouldReadCorrectValue() {
        final MetadataSequencingPolicy metadataPolicy = MetadataSequencingPolicy
                .builder()
                .metadataKey("metadataKey")
                .build();

        DomainEventMessage testEvent =
                newStubDomainEvent("42", Collections.singletonMap("metadataKey", "metadataValue"));

        assertThat(metadataPolicy.getSequenceIdentifierFor(testEvent, new StubProcessingContext())).contains("metadataValue");
    }

    @Test
    void fallbackShouldBeAppliedWhenMetadataDoesNotContainsTheKey() {
        final MetadataSequencingPolicy metadataPolicy = MetadataSequencingPolicy
                .builder()
                .metadataKey("metadataKey")
                .build();

        assertThat(metadataPolicy.getSequenceIdentifierFor(newStubDomainEvent("42"), new StubProcessingContext())).isPresent();
    }

    @Test
    void missingHardRequirementShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> MetadataSequencingPolicy
                             .builder()
                             .build());
    }

    private DomainEventMessage newStubDomainEvent(final Object payload, Map<String, String> metadata) {
        return new GenericDomainEventMessage(
                "aggregateType", "A", 0L, new MessageType("event"), payload, Metadata.from(metadata)
        );
    }

    private DomainEventMessage newStubDomainEvent(final Object payload) {
        return newStubDomainEvent(payload, Collections.emptyMap());
    }
}
