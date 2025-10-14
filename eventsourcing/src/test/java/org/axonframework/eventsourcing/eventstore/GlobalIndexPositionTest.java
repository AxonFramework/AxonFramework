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

package org.axonframework.eventsourcing.eventstore;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link GlobalIndexPosition}.
 *
 * @author John Hendrikx
 */
class GlobalIndexPositionTest {

    private static final GlobalIndexPosition TEN = new GlobalIndexPosition(10);
    private static final GlobalIndexPosition TWENTY = new GlobalIndexPosition(20);
    private static final GlobalIndexPosition MINIMUM = new GlobalIndexPosition(Long.MIN_VALUE);

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
        assertThatThrownBy(() -> TEN.min(new AggregateSequenceNumberPosition(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toIndexShouldConvertValidPositions() {
        assertThat(GlobalIndexPosition.toIndex(TEN)).isEqualTo(10);
        assertThat(GlobalIndexPosition.toIndex(TWENTY)).isEqualTo(20);
        assertThat(GlobalIndexPosition.toIndex(MINIMUM)).isEqualTo(Long.MIN_VALUE);
        assertThat(GlobalIndexPosition.toIndex(Position.START)).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void toIndexShouldRejectInvalidPositions() {
        assertThatThrownBy(() -> GlobalIndexPosition.toIndex(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GlobalIndexPosition.toIndex(new AggregateSequenceNumberPosition(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsAndHashCodeShouldRespectContract() {
        EqualsVerifier.forClass(GlobalIndexPosition.class).verify();
    }
}
