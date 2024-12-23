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

package org.axonframework.eventhandling.async;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MetaDataSequencingPolicy}.
 *
 * @author Lucas Campos
 */
@DisplayName("Unit-Test for the MetaDataSequencingPolicy")
public class MetaDataSequencingPolicyTest {

    @Test
    void propertyShouldReadCorrectValue() {
        final MetaDataSequencingPolicy metaDataPolicy = MetaDataSequencingPolicy
                .builder()
                .metaDataKey("metaDataKey")
                .build();

        DomainEventMessage<?> testEvent =
                newStubDomainEvent("42", Collections.singletonMap("metaDataKey", "metaDataValue"));

        assertEquals("metaDataValue", metaDataPolicy.getSequenceIdentifierFor(testEvent));
    }

    @Test
    void fallbackShouldBeAppliedWhenMetaDataDoesNotContainsTheKey() {
        final MetaDataSequencingPolicy metaDataPolicy = MetaDataSequencingPolicy
                .builder()
                .metaDataKey("metaDataKey")
                .build();

        assertNotNull(metaDataPolicy.getSequenceIdentifierFor(newStubDomainEvent("42")));
    }

    @Test
    void missingHardRequirementShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> MetaDataSequencingPolicy
                             .builder()
                             .build());
    }

    private DomainEventMessage<?> newStubDomainEvent(final Object payload, Map<String, String> metaData) {
        return new GenericDomainEventMessage<>("type", "A", 0L, payload, MetaData.from(metaData));
    }

    private DomainEventMessage<?> newStubDomainEvent(final Object payload) {
        return newStubDomainEvent(payload, Collections.emptyMap());
    }
}
