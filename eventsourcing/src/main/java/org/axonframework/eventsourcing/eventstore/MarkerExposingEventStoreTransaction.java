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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.eventhandling.EventMessage;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Temporary interface to get at the consistency marker for snapshotting.
 * 
 * TODO #4199 Remove this interface, including implementations of the method
 */
@Internal
public interface MarkerExposingEventStoreTransaction {
    MessageStream<? extends EventMessage> source(
        @Nonnull SourcingCondition condition,
        @Nullable AtomicReference<ConsistencyMarker> consistencyMarkerRef
    );
}
