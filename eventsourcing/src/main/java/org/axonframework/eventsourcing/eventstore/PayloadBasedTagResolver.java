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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Simple implementation of the {@link TagResolver} that is able to combine several lambdas to construct
 * {@link Tag Tags} from the event of type {@code E}.
 * <p>
 * The results of invoking all configured lambdas are combined into the {@link Set} of {@code Tags} when invoking
 * {@link #resolve(Object)}.
 *
 * @param <E> The event for which to resolve a {@link Set} of {@link Tag Tags} for.
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class PayloadBasedTagResolver<E> implements TagResolver<E> {

    private final List<Function<E, Tag>> tagResolvers;

    private PayloadBasedTagResolver(List<Function<E, Tag>> tagResolvers) {
        this.tagResolvers = List.copyOf(tagResolvers);
    }

    /**
     * Construct a {@code SimpleTagResolver} for the given {@code eventType}.
     * <p>
     * The initial resolver does not construct any {@link Tag Tags} yet. To add {@code Tags}, additional resolvers can
     * be included through {@link #withResolver(Function)}.
     *
     * @param eventType The  {@code Class} of the event for which to resolve a {@link Set} of {@link Tag Tags} for.
     * @param <E>       The event for which to resolve a {@link Set} of {@link Tag Tags} for.
     * @return A {@code SimpleTagResolver} instance start, still requiring resolvers.
     */
    public static <E> PayloadBasedTagResolver<E> forEvent(@SuppressWarnings("unused") Class<E> eventType) {
        return new PayloadBasedTagResolver<>(new ArrayList<>());
    }

    /**
     * Construct a copy of {@code this SimpleTagResolver}, adding the given {@code tagResolver} to the set.
     *
     * @param tagResolver An additional {@code Function} from the event of type {@code E} to a {@link Tag}.
     * @return A copy of {@code this SimpleTagResolver}, adding the given {@code tagResolver} to the set.
     */
    public PayloadBasedTagResolver<E> withResolver(@Nonnull Function<E, Tag> tagResolver) {
        assertNonNull(tagResolver, "A TagResolver cannot be null");
        List<Function<E, Tag>> copy = new ArrayList<>(tagResolvers);
        copy.add(tagResolver);
        return new PayloadBasedTagResolver<>(copy);
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
    public PayloadBasedTagResolver<E> withResolver(@Nonnull Function<E, String> keyResolver,
                                                   @Nonnull Function<E, String> valueResolver) {
        assertNonNull(keyResolver, "A key resolver cannot be null");
        assertNonNull(valueResolver, "A value resolver cannot be null");
        return withResolver(event -> new Tag(keyResolver.apply(event), valueResolver.apply(event)));
    }

    @Override
    public Set<Tag> resolve(@Nonnull E event) {
        return tagResolvers.stream()
                           .map(tagResolver -> tagResolver.apply(event))
                           .collect(Collectors.toSet());
    }
}
