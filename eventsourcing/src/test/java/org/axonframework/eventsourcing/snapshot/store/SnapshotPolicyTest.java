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

package org.axonframework.eventsourcing.snapshot.store;

import org.axonframework.eventsourcing.snapshot.api.EvolutionResult;
import org.axonframework.eventsourcing.snapshot.api.SnapshotPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link SnapshotPolicy}.
 *
 * @author John Hendrikx
 */
class SnapshotPolicyTest {

    @Test
    void afterEventsPolicyShouldRejectInvalidParameters() {
        assertThatThrownBy(() -> SnapshotPolicy.afterEvents(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnapshotPolicy.afterEvents(Integer.MIN_VALUE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void afterEventsPolicyShouldWorkWithValidParameters() {
        SnapshotPolicy policy = SnapshotPolicy.afterEvents(5);

        assertThat(policy.needsSnapshot(new EvolutionResult(6, Duration.ZERO, false))).isTrue();
        assertThat(policy.needsSnapshot(new EvolutionResult(5, Duration.ZERO, false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(5, Duration.ofDays(1), false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(5, Duration.ZERO, true))).isFalse();
    }

    @Test
    void whenSourcingTimeExceedsPolicyShouldRejectInvalidParameters() {
        assertThatThrownBy(() -> SnapshotPolicy.whenSourcingTimeExceeds(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenSourcingTimeExceedsPolicyShouldWorkWithValidParameters() {
        SnapshotPolicy policy = SnapshotPolicy.whenSourcingTimeExceeds(Duration.ofSeconds(1));

        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ofMillis(1001), false))).isTrue();
        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ofMillis(1000), false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(1000, Duration.ofMillis(1000), false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ofMillis(1000), true))).isFalse();
    }

    @Test
    void whenRequestedPolicyShouldWork() {
        SnapshotPolicy policy = SnapshotPolicy.whenRequested();

        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ZERO, true))).isTrue();
        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ZERO, false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(1000, Duration.ZERO, false))).isFalse();
        assertThat(policy.needsSnapshot(new EvolutionResult(0, Duration.ofDays(1), false))).isFalse();
    }
}
