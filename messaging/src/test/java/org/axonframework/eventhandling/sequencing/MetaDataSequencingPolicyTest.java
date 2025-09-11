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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        final MetaDataSequencingPolicy metaDataPolicy = new MetaDataSequencingPolicy("metaDataKey");

        EventMessage testEvent = EventTestUtils.asEventMessage("42").withMetaData(Map.of("metaDataKey", "metaDataValue"));

        assertThat(metaDataPolicy.getSequenceIdentifierFor(testEvent, new StubProcessingContext())).contains("metaDataValue");
    }

    @Test
    void fallbackShouldBeAppliedWhenMetaDataDoesNotContainsTheKey() {
        final MetaDataSequencingPolicy metaDataPolicy = new MetaDataSequencingPolicy("metaDataKey");

        assertThat(metaDataPolicy.getSequenceIdentifierFor(EventTestUtils.asEventMessage("42"), new StubProcessingContext())).isPresent();
    }

    @Test
    void nullMetaDataKeyShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> new MetaDataSequencingPolicy(null));
    }

    @Test
    void blankMetaDataKeyShouldThrowException() {
        assertThrows(AxonConfigurationException.class,
                     () -> new MetaDataSequencingPolicy("   "));
    }
}
