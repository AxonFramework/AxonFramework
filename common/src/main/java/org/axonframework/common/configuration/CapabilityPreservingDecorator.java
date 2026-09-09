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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * PROOF OF CONCEPT.
 * <p>
 * Wraps a {@link ComponentDecorator} so that it can no longer narrow a component down to fewer interfaces than the
 * {@code delegate} it was given actually implements.
 * <p>
 * This targets the class of bug where a component is registered under a single, most-specific {@link Component.Identifier}
 * (say {@code EventStore.class}), and a decorator written against a broader, assignable type (say {@code EventBus.class})
 * matches and applies to it too -- because {@link DecoratorDefinition.CompletedDecoratorDefinition#matches} is based on
 * assignability, not exact type equality. If that decorator's own output only implements the narrower type it was written
 * for (e.g. a hand-written {@code TracingEventBus implements EventBus}), the component silently loses every interface it
 * had beyond that one, breaking any {@code instanceof}/cast against the wider set once other, later decorators or callers
 * rely on it.
 * <p>
 * This exists to test whether that narrowing can be prevented <b>generically</b>, replacing the two hand-written
 * workarounds this problem currently has in {@code axon-messaging}/{@code axon-eventsourcing}:
 * <ul>
 *     <li>{@code MessagingTracingConfigurationEnhancer}'s {@code isEventStore(delegate)} skip-checks in its
 *     {@code EventSink}/{@code EventBus} decorators, and</li>
 *     <li>{@code TracingEventStore}, which hand-composes a {@code TracingEventSink} internally and forwards every other
 *     {@code EventStore} method straight to the raw delegate, purely to stay assignable to {@code EventStore}.</li>
 * </ul>
 * <p>
 * <b>How it works:</b> after the wrapped {@code decorator} runs, this compares the interfaces implemented by its output
 * against the interfaces implemented by the original {@code delegate}. If nothing was lost, the decorator's own output is
 * returned untouched -- the common case, and free of proxying overhead. If interfaces were lost, a {@link Proxy} is
 * returned instead, implementing the full original interface set: a method declared on an interface the decorated output
 * still implements is routed to that output (so the decoration -- e.g. a tracing span -- still applies); a method declared
 * on an interface only the raw {@code delegate} implements is routed straight to the {@code delegate}, unmodified. This is
 * the same routing rule {@code TracingEventStore} already applies by hand, generalized to any interface combination
 * discovered by reflection, rather than hardcoded per method.
 * <p>
 * <b>Known limitation (by design, for this proof of concept):</b> this only helps when a component is registered under a
 * <em>single</em> {@link Component.Identifier} and reached by decorators matching a broader type through assignability.
 * It does not, by itself, solve the case where a component is deliberately registered under two <em>sibling</em> types
 * that don't extend one another (e.g. {@code EventStorageEngine} and {@code SnapshotStore}, as done for
 * {@code AxonServerEventStorageEngine}) -- that is two separate {@code Identifier} entries, decorated through two
 * separate, independently-memoizing chains, and reconciling those needs {@link DirectionalCapabilityBridge} instead (see
 * its own, more limited, documented behavior).
 *
 * @author Proof of concept -- not for production use as-is.
 */
public final class CapabilityPreservingDecorator {

    private CapabilityPreservingDecorator() {
        // Utility class.
    }

    /**
     * Wraps the given {@code decorator} so its output is guaranteed to still implement every interface the
     * {@code delegate} it receives implements, even if {@code decorator} itself only returns something narrower.
     *
     * @param decorator The decorator to protect against narrowing its delegate.
     * @param <C>       The declared type of the component being decorated.
     * @param <D>       The type the given {@code decorator} itself produces.
     * @return A decorator with identical behavior, except its result is widened back to the full interface set of the
     * {@code delegate} it was given whenever the given {@code decorator} would otherwise have narrowed it.
     */
    @SuppressWarnings("unchecked")
    public static <C, D extends C> ComponentDecorator<C, C> preservingCapabilitiesOf(ComponentDecorator<C, D> decorator) {
        requireNonNull(decorator, "The decorator must not be null.");
        return (config, name, delegate) -> {
            D decorated = decorator.decorate(config, name, delegate);
            if (decorated == null) {
                return null;
            }

            Set<Class<?>> delegateInterfaces = allInterfacesOf(delegate.getClass());
            Set<Class<?>> decoratedInterfaces = allInterfacesOf(decorated.getClass());
            if (decoratedInterfaces.containsAll(delegateInterfaces)) {
                // Nothing was lost -- return the decorator's own output untouched, no proxying needed.
                return decorated;
            }

            return (C) widen(delegate, decorated, delegateInterfaces);
        };
    }

    /**
     * Builds a proxy implementing every interface in {@code interfaces}, routing a method to {@code preferred} when
     * its declaring class is implemented by {@code preferred}, and to {@code fallback} otherwise.
     * <p>
     * Deliberately untyped ({@code Object} in, {@code Object} out): {@code preferred} and {@code fallback} need not be
     * related by inheritance -- {@link DirectionalCapabilityBridge} uses this for two sibling types, neither
     * assignable to the other, where a {@code C}/{@code D extends C} bound would not typecheck at all.
     */
    static Object widen(Object fallback, Object preferred, Set<Class<?>> interfaces) {
        InvocationHandler handler = (proxy, method, args) -> {
            Class<?> declaringClass = method.getDeclaringClass();
            Object target = declaringClass.isInstance(preferred) ? preferred : fallback;
            try {
                return method.invoke(target, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return Proxy.newProxyInstance(
                fallback.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                handler
        );
    }

    static Set<Class<?>> allInterfacesOf(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        collectInterfaces(type, result);
        return result;
    }

    private static void collectInterfaces(@Nullable Class<?> type, Set<Class<?>> into) {
        if (type == null || type == Object.class) {
            return;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (into.add(iface)) {
                collectInterfaces(iface, into);
            }
        }
        collectInterfaces(type.getSuperclass(), into);
    }
}
