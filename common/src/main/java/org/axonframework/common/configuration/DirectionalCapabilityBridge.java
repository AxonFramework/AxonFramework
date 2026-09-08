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

import org.jspecify.annotations.Nullable;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * PROOF OF CONCEPT -- SUPERSEDED for the motivating {@code EventStorageEngine}/{@code SnapshotStore} case.
 * <p>
 * This class was the first approach tried for that case and is kept for what it documents (the reentrancy wall is
 * real and worth knowing about), but {@link DecoratingComponent} turned out to solve AxonIQ/axoniq-framework#397's
 * actual reported problem directly, more simply, and with no reentrancy risk at all -- see its javadoc and
 * {@code SnapshotCapableEventStorageEngine#decorate}. Reach for this class only if a future case genuinely needs
 * both sibling slots to expose the other's full interface set (not just correct self-hosting detection), and its
 * {@code primary} type is verified independent of {@code secondary} first.
 * <p>
 * Addresses the narrowing problem {@link CapabilityPreservingDecorator} solves, for the case that utility explicitly
 * cannot handle: a component registered under two <em>sibling</em> types that don't extend one another -- neither is
 * assignable to the other, so there is no single {@link Component.Identifier} for decorator-matching to converge on.
 * {@code AxonServerEventStorageEngine} is the motivating, real-world example: it implements both
 * {@code EventStorageEngine} and {@code SnapshotStore} directly, and {@code AxonServerConfigurationEnhancer} registers
 * the very same instance under both {@code EventStorageEngine.class} and {@code SnapshotStore.class} -- two genuinely
 * separate {@code Identifier} entries in {@link Components}, decorated through two separate, independently-memoizing
 * chains.
 * <p>
 * This is the structural problem behind AxonIQ/axoniq-framework#397 and AxonIQ/AxonFramework#5039:
 * {@code SnapshotCapableEventStorageEngine#decorate(EventStorageEngine, SnapshotStore)} used to compare the two
 * arguments by identity (later, by an {@code instanceof SnapshotStore} check on the engine argument alone) to decide
 * whether to leave a self-hosting engine undecorated. Both versions only look at the {@code EventStorageEngine.class}
 * side; neither reconciles what {@code SnapshotStore.class} independently resolves to.
 * <p>
 * <b>Why this can only be one-directional:</b> a fully symmetric fix -- where resolving <em>either</em> slot, in
 * <em>either</em> order, returns the identical, fully-capable merged object -- was attempted first, using a shared
 * memoization cell with a reentrancy guard so slot A's decorator could safely ask for slot B's (and vice versa)
 * without infinite recursion. It does not work, and the reason is fundamental, not a bug in the guard: this
 * registry's {@link Component#resolve(Configuration)} contract memoizes on the <em>first</em> call, permanently
 * ("Subsequent calls to this method will result in the same instance"). Whichever slot is reached
 * <em>reentrantly</em> -- nested inside the other slot's own, still-in-progress resolution -- necessarily returns
 * its pre-merge value to break the cycle, and that value is what gets cached forever, poisoning every later,
 * independent call to that slot. There is no lazy, purely decorator-based way around this; it needs the registry
 * itself to know two identifiers share one instance and decorate that shared instance exactly once (see
 * {@code Components}' flat, one-identifier-per-entry map -- there is no such concept today).
 * <p>
 * So this utility picks one type as {@code primary} and leaves it to resolve completely independently, exactly as it
 * would without this class existing. It then registers a decorator on the {@code secondary} type's slot, which, once
 * that slot's own decorator chain has run, asks for the (by then fully resolved and cached) {@code primary}
 * component and widens the secondary slot's result back so it also implements everything {@code primary} does --
 * using the same routing rule as {@link CapabilityPreservingDecorator}: a method the secondary slot's own decoration
 * covers is routed there (so e.g. a tracing span on the secondary capability still applies); everything else is
 * routed to {@code primary}.
 * <p>
 * <b>Consequence:</b> both slots end up implementing the full capability set (no more {@code ClassCastException} /
 * narrowing surprises, and {@code instanceof} checks against either type succeed on either slot's resolved value),
 * but only {@code secondary}'s resolution reflects decorations applied to {@code primary}. Decorations applied only
 * to {@code secondary} are invisible when resolving {@code primary} directly. Whether that is acceptable depends on
 * whether application code is guaranteed to always resolve through {@code secondary} -- for the
 * {@code EventStorageEngine}/{@code SnapshotStore} case, the framework itself always resolves
 * {@code EventStorageEngine.class} (see {@code SnapshotSourcingConfigurationEnhancer}), so picking
 * {@code EventStorageEngine} as {@code primary} and {@code SnapshotStore} as {@code secondary} covers every
 * in-framework call site, at the cost of a direct {@code config.getComponent(SnapshotStore.class)} not reflecting
 * ESE-side decorations (which nothing in-tree currently does anyway).
 * <p>
 * <b>This still requires {@code primary} to be genuinely independent of {@code secondary}, and that does not always
 * hold.</b> Tried against the real {@code SnapshotSourcingConfigurationEnhancer} -- registering
 * {@code bridgeOnto(registry, EventStorageEngine.class, SnapshotStore.class, ...)} there, on top of its existing
 * {@code EventStorageEngine.class} decorator -- and it produces a real, reproducible {@code StackOverflowError}, not
 * a theoretical one. That decorator already calls {@code config.getOptionalComponent(SnapshotStore.class)} itself,
 * to build the {@code SnapshotCapableEventStorageEngine} wrapper for the common case of a genuinely separate engine
 * and store. Adding this bridge on {@code SnapshotStore.class} makes it reach back into
 * {@code EventStorageEngine.class}, so the two slots become mutually, lazily dependent: resolving one nests into the
 * other's decorator, which nests back into the first (still mid-resolution), without end. In other words,
 * {@code EventStorageEngine} was never a safe {@code primary} for this pairing to begin with -- it has its own
 * legitimate reason to depend on {@code secondary}. This utility only works when {@code primary} can be resolved
 * with no knowledge of {@code secondary} at all; verify that before reaching for it, not after.
 *
 * @author Proof of concept -- not for production use as-is.
 */
public final class DirectionalCapabilityBridge {

    private DirectionalCapabilityBridge() {
        // Utility class.
    }

    /**
     * Registers a decorator on {@code secondaryType}'s slot (optionally scoped to {@code name}) that widens its
     * result back to also implement everything the given {@code primaryType} component implements, whenever the two
     * are meant to be the same shared instance but {@code secondaryType}'s own decorator chain would otherwise leave
     * it narrower.
     * <p>
     * If the currently resolved {@code primaryType} component does not itself implement {@code secondaryType}, this
     * is a no-op decorator (returns the secondary slot's value untouched) -- the two components are, in that case,
     * genuinely unrelated rather than aliases of one shared instance, and nothing needs bridging.
     *
     * @param registry      The registry to register the bridging decorator with.
     * @param primaryType   The type whose resolution is left untouched and treated as authoritative.
     * @param secondaryType The type whose resolution is widened back to also cover {@code primaryType}.
     * @param name          The name of the component to bridge, or {@code null} for the unnamed component.
     * @param order         The order to register the bridging decorator at -- must run after every other decorator
     *                      registered for {@code secondaryType} that should be preserved.
     * @param <A>           The primary, authoritative type.
     * @param <B>           The secondary type, whose resolution is widened.
     */
    public static <A, B> void bridgeOnto(ComponentRegistry registry,
                                         Class<A> primaryType,
                                         Class<B> secondaryType,
                                         @Nullable String name,
                                         int order) {
        requireNonNull(registry, "The registry must not be null.");
        requireNonNull(primaryType, "The primaryType must not be null.");
        requireNonNull(secondaryType, "The secondaryType must not be null.");

        ComponentDecorator<B, B> bridge = (config, n, delegateB) -> {
            A primary = config.getComponent(primaryType, name);
            if (!secondaryType.isInstance(primary)) {
                // primary isn't secondary-capable at all -- these two components aren't aliases of one shared
                // instance, nothing to bridge.
                return delegateB;
            }
            if (primary == delegateB) {
                return delegateB;
            }

            Set<Class<?>> primaryInterfaces = CapabilityPreservingDecorator.allInterfacesOf(primary.getClass());
            Set<Class<?>> secondaryInterfaces = CapabilityPreservingDecorator.allInterfacesOf(delegateB.getClass());
            if (secondaryInterfaces.containsAll(primaryInterfaces)) {
                return delegateB;
            }
            return secondaryType.cast(CapabilityPreservingDecorator.widen(primary, delegateB, primaryInterfaces));
        };

        if (name == null) {
            registry.registerDecorator(secondaryType, order, bridge);
        } else {
            registry.registerDecorator(secondaryType, name, order, bridge);
        }
    }
}
