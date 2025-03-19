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

import javax.annotation.Nonnull;

/**
 * Interface describing the mechanism for triggering Snapshots. The SnapshotTriggerDefinition creates specific trigger
 * instances for each Aggregate. The lifecycle of these triggers is attached to the lifecycle of the aggregate itself.
 * This means the trigger is cached if the aggregate is, and it is disposed together with the aggregate.
 * <p>
 * This means aggregate-specific state should be kept in the {@link SnapshotTrigger} instances, and not in the
 * {@code SnapshotTriggerDefinition}
 *
 * @author Allard Buijze
 * @since 3.0
 */
public interface SnapshotTriggerDefinition {

    /**
     * Prepares a new trigger for an aggregate with the given {@code aggregateIdentifier} and {@code aggregateType}. The
     * trigger will be notified of each event applied on the aggregate, as well as the moment at which the aggregate
     * state is fully initialized based on its historic events.
     * <p>
     * Any resources that the trigger needs can be reattached by implementing the
     * {@link #reconfigure(Class, SnapshotTrigger)} method. This method is invoked when a SnapshotTrigger has been
     * deserialized.
     *
     * @param aggregateType The type of aggregate for which to create a trigger
     * @return the trigger instance for an aggregate
     */
    SnapshotTrigger prepareTrigger(@Nonnull Class<?> aggregateType);

    /**
     * Reconfigure the necessary infrastructure components in the given {@code trigger instance}, which may have been
     * lost in the (de)serialization process.
     * <p>
     * Since implementations of the {@link SnapshotTrigger} often rely on a {@link Snapshotter} which cannot be
     * serialized, it may be necessary to inject these resources after deserialization of a trigger.
     * <p>
     * Implementations returning a SnapshotTrigger, should implement this method if
     * not all fields could be initialized base on serialized data.
     *
     * @param aggregateType The type of aggregate for which this trigger was created
     * @param trigger       The trigger instance formerly created using {@link #prepareTrigger(Class)}
     * @return a fully (re)configured trigger instance, may be the same {@code trigger} instance.
     */
    default SnapshotTrigger reconfigure(@Nonnull Class<?> aggregateType, @Nonnull SnapshotTrigger trigger) {
        return trigger;
    }
}
