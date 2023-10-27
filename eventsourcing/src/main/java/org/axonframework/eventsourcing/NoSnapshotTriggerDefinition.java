/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;

import java.io.Serializable;
import javax.annotation.Nonnull;

/**
 * Implementation of {@link SnapshotTriggerDefinition} that doesn't trigger snapshots at all.
 */
public enum NoSnapshotTriggerDefinition implements SnapshotTriggerDefinition {

    /**
     * The singleton instance of a {@link NoSnapshotTriggerDefinition}.
     */
    INSTANCE;

    /**
     * A singleton instance of a {@link SnapshotTrigger} that does nothing.
     */
    public static final SnapshotTrigger TRIGGER = new NoSnapshotTrigger();

    @Override
    public SnapshotTrigger prepareTrigger(@Nonnull Class<?> aggregateType) {
        return TRIGGER;
    }

    private static class NoSnapshotTrigger implements SnapshotTrigger, Serializable {

        @Override
        public void eventHandled(@Nonnull EventMessage<?> msg) {
            // No operation necessary for a no-op implementation.
        }

        @Override
        public void initializationFinished() {
            // No operation necessary for a no-op implementation.
        }
    }
}
