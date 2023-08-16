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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class NoSnapshotTriggerDefinitionTest {

    @Test
    void triggerDefinitionReturnsSameInstance() {
        SnapshotTrigger instance1 = NoSnapshotTriggerDefinition.INSTANCE.prepareTrigger(Object.class);
        SnapshotTrigger instance2 = NoSnapshotTriggerDefinition.INSTANCE.prepareTrigger(Object.class);
        SnapshotTrigger instance3 = NoSnapshotTriggerDefinition.INSTANCE.prepareTrigger(Object.class);

        assertSame(instance1, instance2);
        assertSame(instance1, instance3);
        assertSame(instance2, instance3);

    }
}
