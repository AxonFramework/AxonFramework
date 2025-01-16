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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simple implementation of the {@link TagResolver} that is able to combine several lambdas to construct
 * {@link Tag Tags} from payloads of type {@code P}.
 * <p>
 * The results of invoking all configured lambdas are combined into the {@link Set} of {@code Tags} when invoking
 * {@link #resolve(EventMessage)}. The {@link #resolve(EventMessage) resolve} operation defaults to return an empty
 * {@code Set} when an {@link EventMessage} for another payload type is passed.
 *
 * @param <P> The payload for which to resolve a {@link Set} of {@link Tag Tags} for.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class PayloadBasedTagResolver<P> implements TagResolver {

    private final Class<P> payloadType;
    private final List<Function<P, Tag>> resolvers;

    private PayloadBasedTagResolver(Class<P> payloadType, List<Function<P, Tag>> resolvers) {
        this.payloadType = payloadType;
        this.resolvers = List.copyOf(resolvers);
    }

    /**
     * Construct a {@code SimpleTagResolver} for the given {@code payloadType}.
     * <p>
     * The initial resolver does not construct any {@link Tag Tags} yet. To add {@code Tags}, additional resolvers can
     * be included through {@link #withResolver(Function)}.
     *
     * @param payloadType The {@code Class} of the event payload for which to resolve a {@link Set} of {@link Tag Tags}
     *                    for.
     * @param <P>         The payload for which to resolve a {@link Set} of {@link Tag Tags} for.
     * @return A {@code SimpleTagResolver} instance start, still requiring resolvers.
     */
    public static <P> PayloadBasedTagResolver<P> forPayloadType(Class<P> payloadType) {
        return new PayloadBasedTagResolver<>(payloadType, new ArrayList<>());
    }

    /**
     * Construct a copy of {@code this SimpleTagResolver}, adding the given {@code resolver} to the set.
     *
     * @param resolver An additional {@code Function} from the event of type {@code P} to a {@link Tag}.
     * @return A copy of {@code this SimpleTagResolver}, adding the given {@code resolver} to the set.
     */
    public PayloadBasedTagResolver<P> withResolver(@Nonnull Function<P, Tag> resolver) {
        List<Function<P, Tag>> copy = new ArrayList<>(resolvers);
        copy.add(Objects.requireNonNull(resolver, "A TagResolver cannot be null"));
        return new PayloadBasedTagResolver<>(payloadType, copy);
    }

    /**
     * Construct a copy of {@code this SimpleTagResolver}, combining the given {@code keyResolver} and
     * {@code valueResolver} into a lambda constructing a {@link Tag}.
     *
     * @param keyResolver   A {@code Function} constructing the {@link Tag#key()} for a {@link Tag}.
     * @param valueResolver A {@code Function} constructing the {@link Tag#value()} for a {@link Tag}.
     * @return A copy of {@code this SimpleTagResolver}, combining the given {@code keyResolver} and
     * {@code valueResolver} into a lambda constructing a {@link Tag}.
     */
    public PayloadBasedTagResolver<P> withResolver(@Nonnull Function<P, String> keyResolver,
                                                   @Nonnull Function<P, String> valueResolver) {
        Function<P, String> keyLambda = Objects.requireNonNull(keyResolver, "A key resolver cannot be null");
        Function<P, String> valueLambda = Objects.requireNonNull(valueResolver,
                                                                 "A valueLambda resolver cannot be null");
        return withResolver(event -> new Tag(keyLambda.apply(event), valueLambda.apply(event)));
    }

    @Override
    public Set<Tag> resolve(@Nonnull EventMessage<?> event) {
        //noinspection unchecked - suppressing cast warning
        return payloadType.isAssignableFrom(event.getPayload().getClass())
                ? resolvers.stream()
                           .map(resolver -> resolver.apply((P) event.getPayload()))
                           .collect(Collectors.toSet())
                : Set.of();
    }
}
