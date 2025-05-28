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

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.unitofwork.SimpleProcessingContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Full implementation of the {@link ProcessingContext} that allows for registering lifecycle actions.
 * This is used for test situations where we want to supply a mock {@link ProcessingContext} to a
 * component that requires it. The phase can then be moved forward through {@link #moveToPhase(Phase)}, and the actions registered for the
 * phases in between will be executed.
 *
 * @author Allard Buijze
 */
public class StubProcessingContext extends SimpleProcessingContext {

    private final Map<Phase, List<Function<ProcessingContext, CompletableFuture<?>>>> phaseActions = new ConcurrentHashMap<>();
    private final Phase currentPhase = DefaultPhases.PRE_INVOCATION;

    private StubProcessingContext() {
        // Should be created with one of the static methods
    }

    public CompletableFuture<Object> moveToPhase(Phase phase) {
        if (phase.isBefore(currentPhase)) {
            throw new IllegalArgumentException("Cannot move to a phase before the current phase");
        }
        if (!phase.isAfter(currentPhase)) {
            return CompletableFuture.completedFuture(null);
        }
        return phaseActions.keySet().stream()
                           .filter(p -> p.isAfter(currentPhase) && p.order() <= phase.order())
                           .sorted(Comparator.comparing(Phase::order))
                           .flatMap(p -> phaseActions.get(p).stream())
                           .reduce(CompletableFuture.completedFuture(null),
                                   (cf, action) -> cf.thenCompose(v -> (CompletableFuture<Object>) action.apply(this)),
                                   (cf1, cf2) -> cf2);
    }

    @Override
    public ProcessingLifecycle on(Phase phase, Function<ProcessingContext, CompletableFuture<?>> action) {
        if(phase.order() <= currentPhase.order()) {
            throw new IllegalArgumentException("Cannot register an action for a phase that has already passed");
        }
        phaseActions.computeIfAbsent(phase, p -> new CopyOnWriteArrayList<>()).add(action);
        return this;
    }

    public static ProcessingContext forMessage(Message<?> message) {
        StubProcessingContext stubProcessingContext = new StubProcessingContext();
        stubProcessingContext.putResource(Message.RESOURCE_KEY, message);
        return stubProcessingContext;
    }

    public static ProcessingContext forUnitOfWork(LegacyUnitOfWork<?> uow) {
        StubProcessingContext stubProcessingContext = new StubProcessingContext();
        stubProcessingContext.putResource(Message.RESOURCE_KEY, uow.getMessage());
        return stubProcessingContext;
    }
}
