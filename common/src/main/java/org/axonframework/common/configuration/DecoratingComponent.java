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

package org.axonframework.common.configuration;

/**
 * PROOF OF CONCEPT.
 * <p>
 * Implemented by a hand-written {@link ComponentDecorator} output class (e.g. a {@code TracingSnapshotStore}) to
 * expose the single delegate it wraps, so code holding the decorated value can see through it back to whatever it was
 * built from.
 * <p>
 * This exists for a narrower, safer purpose than {@link CapabilityPreservingDecorator}/
 * {@link DirectionalCapabilityBridge}: those solve "does the decorated value still implement every interface its
 * delegate did." This solves a different, more targeted question that turned out to be the one the original
 * AxonIQ/axoniq-framework#397 bug actually depends on: "is this decorated value, underneath any decoration applied to
 * it, the very same instance as some other, independently obtained reference" -- e.g. is a possibly-{@code
 * TracingSnapshotStore}-wrapped {@code SnapshotStore.class} component, underneath that wrapping, the very same
 * self-hosting {@code AxonServerEventStorageEngine} instance resolved separately as {@code EventStorageEngine.class}.
 * <p>
 * Answering that with an {@code instanceof} check alone (as AxonFramework#5039 proposes) is unsound: it cannot tell
 * "this engine happens to also implement SnapshotStore" from "this engine IS the SnapshotStore in question" -- see
 * {@code SnapshotCapableEventStorageEngineTest} and the {@code EventSourcingConfigurationDefaultsTest} regression this
 * proof of concept found when trying that check verbatim. Chasing the unwrap chain down to its root and comparing
 * <em>that</em> by identity recovers the precision the original {@code engine == snapshotStore} check had, without
 * that check's fragility under decoration -- and, unlike {@link DirectionalCapabilityBridge}, needs no
 * {@code Configuration#getComponent} call at all, so it carries no reentrancy/cycle risk whatsoever: it is a plain,
 * synchronous walk over object references already in hand.
 *
 * @author Proof of concept -- not for production use as-is.
 */
public interface DecoratingComponent {

    /**
     * Returns the single delegate this decorator wraps.
     *
     * @return the wrapped delegate; never a {@code DecoratingComponent} that wraps a further delegate without
     * {@code this} object being aware of it, so {@link #unwrapFully(Object)} always terminates.
     */
    Object decoratedDelegate();

    /**
     * Follows the {@link #decoratedDelegate()} chain from the given {@code value} down to its root, returning the
     * first value encountered that is not itself a {@link DecoratingComponent}.
     * <p>
     * Returns {@code value} itself, unchanged, when it is not a {@code DecoratingComponent} to begin with.
     *
     * @param value the value to unwrap.
     * @return the root delegate at the bottom of {@code value}'s decoration chain, or {@code value} itself if it is
     * not decorated.
     */
    static Object unwrapFully(Object value) {
        Object current = value;
        while (current instanceof DecoratingComponent decorating) {
            current = decorating.decoratedDelegate();
        }
        return current;
    }
}
