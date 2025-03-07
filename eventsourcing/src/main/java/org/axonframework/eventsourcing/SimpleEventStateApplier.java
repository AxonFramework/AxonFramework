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

package org.axonframework.eventsourcing;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class SimpleEventStateApplier<S> implements EventStateApplier<S> {

    private final ConcurrentHashMap<QualifiedName, EventStateApplier<S>> eventStateAppliers;

    public SimpleEventStateApplier() {
        this.eventStateAppliers = new ConcurrentHashMap<>();
    }

    public SimpleEventStateApplier<S> on(QualifiedName name, EventStateApplier<S> applier) {
        eventStateAppliers.put(name, applier);
        return this;
    }

    @Override
    public S apply(@Nonnull S state, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext processingContext) {
        return null;
    }
}
