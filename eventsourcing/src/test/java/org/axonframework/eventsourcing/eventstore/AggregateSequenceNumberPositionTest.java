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

package org.axonframework.eventsourcing.eventstore;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link AggregateSequenceNumberPosition}.
 *
 * @author John Hendrikx
 */
class AggregateSequenceNumberPositionTest {

    private static final AggregateSequenceNumberPosition TEN = new AggregateSequenceNumberPosition(10);
    private static final AggregateSequenceNumberPosition TWENTY = new AggregateSequenceNumberPosition(20);
    private static final AggregateSequenceNumberPosition MINIMUM = new AggregateSequenceNumberPosition(0);

    @Test
    void constructorShouldRejectInvalidArguments() {
        assertThatThrownBy(() -> new AggregateSequenceNumberPosition(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void minShouldReturnSmallestPositionAndBeSymmetric() {
        assertThat(TEN.min(TWENTY)).isEqualTo(TEN);
        assertThat(TWENTY.min(TEN)).isEqualTo(TEN);
        assertThat(MINIMUM.min(TEN)).isEqualTo(MINIMUM);
        assertThat(TEN.min(MINIMUM)).isEqualTo(MINIMUM);

        // Position.START should always be smaller:
        assertThat(TEN.min(Position.START)).isEqualTo(Position.START);
        assertThat(Position.START.min(TEN)).isEqualTo(Position.START);
        assertThat(MINIMUM.min(Position.START)).isEqualTo(Position.START);
        assertThat(Position.START.min(MINIMUM)).isEqualTo(Position.START);
    }

    @Test
    void minShouldRejectInvalidPositions() {
        assertThatThrownBy(() -> TEN.min(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TEN.min(new GlobalIndexPosition(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toIndexShouldConvertValidPositions() {
        assertThat(AggregateSequenceNumberPosition.toSequenceNumber(TEN)).isEqualTo(10);
        assertThat(AggregateSequenceNumberPosition.toSequenceNumber(TWENTY)).isEqualTo(20);
        assertThat(AggregateSequenceNumberPosition.toSequenceNumber(MINIMUM)).isEqualTo(0);
        assertThat(AggregateSequenceNumberPosition.toSequenceNumber(Position.START)).isEqualTo(0);
    }

    @Test
    void toIndexShouldRejectInvalidPositions() {
        assertThatThrownBy(() -> AggregateSequenceNumberPosition.toSequenceNumber(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AggregateSequenceNumberPosition.toSequenceNumber(new GlobalIndexPosition(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCodeShouldRespectContract() {
        EqualsVerifier.forClass(AggregateSequenceNumberPosition.class).verify();
    }
}
