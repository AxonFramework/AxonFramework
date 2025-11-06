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

package org.axonframework.conversion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

/**
 * A {@link ContentTypeConverter} implementation that delegates to a chain of other {@code ContentTypeConverters} to
 * convert from a source to a target for which there is not necessarily a single converter available.
 *
 * @param <S> The source type of the converter.
 * @param <T> The target type of the converter.
 * @author Allard Buijze
 * @since 2.0.0
 */
public class ChainedConverter<S, T> implements ContentTypeConverter<S, T> {

    private final Class<S> source;
    private final Class<T> target;
    private final List<ContentTypeConverter<?, ?>> delegates;

    /**
     * Returns a {@code ChainedConverter} that can convert an object from the given {@code sourceType} to the given
     * {@code targetType} using a chain formed with the given {@code candidates}.
     * <p>
     * The returned converter uses some, or all, of the given {@code candidates} as delegates.
     *
     * @param sourceType The source type of the converter.
     * @param targetType The target type of the converter.
     * @param candidates The candidates to form a chain with.
     * @param <S>        The source type of the converter.
     * @param <T>        The target type of the converter.
     * @return A converter for the given source and target types.
     * @throws ConversionException if no converter can be created using given candidates.
     */
    public static <S, T> ChainedConverter<S, T> calculateChain(
            @Nonnull Class<S> sourceType,
            @Nonnull Class<T> targetType,
            @Nonnull Collection<ContentTypeConverter<?, ?>> candidates
    ) {
        Route route = calculateRoute(sourceType, targetType, candidates);
        if (route == null) {
            throw new ConversionException(format("Cannot build a converter to convert from %s to %s",
                                                 sourceType.getName(), targetType.getName()));
        }
        return new ChainedConverter<>(route.asList());
    }

    /**
     * Indicates whether this converter is capable of converting the given {@code sourceContentType} into
     * {@code targetContentType}, using the given {@code converters}.
     * <p>
     * When {@code true}, it may use any number of the given {@code converters} to form a chain.
     *
     * @param sourceContentType The content type of the source object.
     * @param targetContentType The content type of the target object.
     * @param converters        The converters eligible for use.
     * @param <S>               The content type of the source object.
     * @param <T>               The content type of the target object.
     * @return {@code true} if this {@code ChainedConverter} can convert between the given {@code sourceContentType} and
     * {@code targetContentType}, using the given {@code converters}, Otherwise {@code false}.
     */
    public static <S, T> boolean canConvert(@Nonnull Class<S> sourceContentType,
                                            @Nonnull Class<T> targetContentType,
                                            @Nonnull List<ContentTypeConverter<?, ?>> converters) {
        return calculateRoute(sourceContentType, targetContentType, converters) != null;
    }

    private static <S, T> Route calculateRoute(Class<S> sourceType,
                                               Class<T> targetType,
                                               Collection<ContentTypeConverter<?, ?>> candidates) {
        return new RouteCalculator(candidates).calculateRoute(sourceType, targetType);
    }

    /**
     * Creates a new instance that uses the given {@code delegates} to form a chain of converters.
     * <p>
     * Note that the {@code delegates} must for a continuous chain, meaning that each item must produce an object of a
     * type that can be consumed by the next delegate.
     * <p/>
     * To automatically calculate a route between converters, see
     * {@link #calculateChain(Class source, Class target, java.util.Collection candidates) calculateChain(source,
     * target, candidates)}
     *
     * @param delegates The chain of delegates to perform the conversion.
     */

    public ChainedConverter(@Nonnull List<ContentTypeConverter<?, ?>> delegates) {
        Assert.isTrue(!delegates.isEmpty(), () -> "The given delegates may not be null or empty");
        Assert.isTrue(isContinuous(delegates), () -> "The given delegates must form a continuous chain");
        this.delegates = new ArrayList<>(delegates);
        //noinspection unchecked
        this.source = (Class<S>) this.delegates.getFirst().expectedSourceType();
        //noinspection unchecked
        this.target = (Class<T>) this.delegates.getLast().targetType();
    }

    private boolean isContinuous(List<ContentTypeConverter<?, ?>> candidates) {
        Class<?> current = null;
        for (ContentTypeConverter<?, ?> candidate : candidates) {
            if (current == null || current.equals(candidate.expectedSourceType())) {
                current = candidate.targetType();
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    @Nonnull
    public Class<S> expectedSourceType() {
        return source;
    }

    @Override
    @Nonnull
    public Class<T> targetType() {
        return target;
    }

    @Override
    @Nullable
    public T convert(@Nullable S input) {
        Object intermediate = input;
        //noinspection rawtypes - Supressed inspection as each step has a different type
        for (ContentTypeConverter step : delegates) {
            //noinspection unchecked
            intermediate = step.convert(intermediate);
        }
        //noinspection ReassignedVariable,unchecked
        return (T) intermediate;
    }

    /**
     * Instance that calculates a "route" through a number of {@link ContentTypeConverter ContentTypeConverters} using
     * Dijkstra's algorithm.
     */
    private static final class RouteCalculator {

        private final Set<ContentTypeConverter<?, ?>> candidates;
        private final List<Route> routes = new LinkedList<>();

        private RouteCalculator(Collection<ContentTypeConverter<?, ?>> candidates) {
            this.candidates = new HashSet<>(candidates);
        }

        private Route calculateRoute(Class<?> sourceType, Class<?> targetType) {
            Route match = buildInitialRoutes(sourceType, targetType);
            if (match != null) {
                return match;
            }
            while (!candidates.isEmpty() && !routes.isEmpty()) {
                Route route = getShortestRoute();
                for (ContentTypeConverter<?, ?> candidate : new HashSet<>(candidates)) {
                    if (route.endPoint().equals(candidate.expectedSourceType())) {
                        Route newRoute = route.joinedWith(candidate);
                        candidates.remove(candidate);
                        if (targetType.equals(newRoute.endPoint())) {
                            return newRoute;
                        }
                        routes.add(newRoute);
                    }
                }
                routes.remove(route);
            }
            return null;
        }

        private Route buildInitialRoutes(Class<?> sourceType, Class<?> targetType) {
            for (ContentTypeConverter<?, ?> converter : new HashSet<>(candidates)) {
                if (converter.expectedSourceType().isAssignableFrom(sourceType)) {
                    Route route = new Route(converter);
                    if (targetType.isAssignableFrom(route.endPoint())) {
                        return route;
                    }
                    routes.add(route);
                    candidates.remove(converter);
                }
            }
            return null;
        }

        private Route getShortestRoute() {
            // since all nodes have equal distance, the first (i.e. oldest) node is the shortest
            return routes.getFirst();
        }
    }

    private static final class Route {

        private final ContentTypeConverter<?, ?>[] nodes;
        private final Class<?> endPoint;

        private Route(ContentTypeConverter<?, ?> initialVertex) {
            this.nodes = new ContentTypeConverter[]{initialVertex};
            endPoint = initialVertex.targetType();
        }

        private Route(ContentTypeConverter<?, ?>[] baseNodes, ContentTypeConverter<?, ?> newDestination) {
            nodes = Arrays.copyOf(baseNodes, baseNodes.length + 1);
            nodes[baseNodes.length] = newDestination;
            endPoint = newDestination.targetType();
        }

        private Route joinedWith(ContentTypeConverter<?, ?> newVertex) {
            Assert.isTrue(endPoint.equals(newVertex.expectedSourceType()),
                          () -> "Cannot append a vertex if it does not start where the current Route ends");
            return new Route(nodes, newVertex);
        }

        private Class<?> endPoint() {
            return endPoint;
        }

        private List<ContentTypeConverter<?, ?>> asList() {
            return Arrays.asList(nodes);
        }
    }
}
