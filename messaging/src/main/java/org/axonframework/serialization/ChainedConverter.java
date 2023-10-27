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

package org.axonframework.serialization;

import org.axonframework.common.Assert;

import java.util.*;

import static java.lang.String.format;

/**
 * A converter that delegates to a chain of other ContentTypeConverters to convert from a source to a target for which
 * there is not necessarily a single converter available.
 *
 * @param <S> The source type of the converter
 * @param <T> The target type of the converter
 * @author Allard Buijze
 * @since 2.0
 */
public class ChainedConverter<S, T> implements ContentTypeConverter<S, T> {

    private final List<ContentTypeConverter<?,?>> delegates;
    private final Class<T> target;
    private final Class<S> source;

    /**
     * Returns a converter that can convert an IntermediateRepresentation from the given {@code sourceType} to the
     * given {@code targetType} using a chain formed with given {@code candidates}. The returned converter
     * uses some (or all) of the given {@code candidates} as delegates.
     *
     * @param sourceType The source type of the converter
     * @param targetType The target type of the converter
     * @param candidates The candidates to form a chain with
     * @param <S>        The source type of the converter
     * @param <T>        The target type of the converter
     * @return A converter for the given source and target types
     *
     * @throws CannotConvertBetweenTypesException
     *          if no converter can be created using given candidates
     */
    public static <S, T> ChainedConverter<S, T> calculateChain(Class<S> sourceType, Class<T> targetType,
                                                               Collection<ContentTypeConverter<?, ?>> candidates) {
        Route route = calculateRoute(sourceType, targetType, candidates);
        if (route == null) {
            throw new CannotConvertBetweenTypesException(format("Cannot build a converter to convert from %s to %s",
                                                                sourceType.getName(), targetType.getName()));
        }
        return new ChainedConverter<>(route.asList());
    }

    /**
     * Indicates whether this converter is capable of converting the given {@code sourceContentType} into
     * {@code targetContentType}, using the given {@code converters}. When {@code true}, it may use any
     * number of the given {@code converters} to form a chain.
     *
     * @param sourceContentType The content type of the source object
     * @param targetContentType The content type of the target object
     * @param converters        The converters eligible for use
     * @param <S>               The content type of the source object
     * @param <T>               The content type of the target object
     * @return {@code true} if this Converter can convert between the given types, using the given converters.
     *         Otherwise {@code false}.
     */
    public static <S, T> boolean canConvert(Class<S> sourceContentType, Class<T> targetContentType,
                                            List<ContentTypeConverter<?, ?>> converters) {
        return calculateRoute(sourceContentType, targetContentType, converters) != null;
    }

    private static <S, T> Route calculateRoute(Class<S> sourceType, Class<T> targetType,
                                               Collection<ContentTypeConverter<?, ?>> candidates) {
        return new RouteCalculator(candidates).calculateRoute(sourceType, targetType);
    }

    /**
     * Creates a new instance that uses the given {@code delegates} to form a chain of converters. Note that the
     * {@code delegates} must for a Continuous chain, meaning that each item must produce an
     * IntermediateRepresentation of a type that can be consumed by the next delegate.
     * <p/>
     * To automatically calculate a route between converters, see {@link #calculateChain(Class source, Class target,
     * java.util.Collection candidates) calculateChain(source, target, candidates)}
     *
     * @param delegates the chain of delegates to perform the conversion
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public ChainedConverter(List<ContentTypeConverter<?,?>> delegates) {
        Assert.isTrue(delegates != null && !delegates.isEmpty(), () -> "The given delegates may not be null or empty");
        Assert.isTrue(isContinuous(delegates), () -> "The given delegates must form a continuous chain");
        this.delegates = new ArrayList<>(delegates);
        target = (Class<T>) this.delegates.get(this.delegates.size() - 1).targetType();
        source = (Class<S>) delegates.get(0).expectedSourceType();
    }

    private boolean isContinuous(List<ContentTypeConverter<?,?>> candidates) {
        Class current = null;
        for (ContentTypeConverter candidate : candidates) {
            if (current == null || current.equals(candidate.expectedSourceType())) {
                current = candidate.targetType();
            } else {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public T convert(S original) {
        Object intermediate = original;
        for (ContentTypeConverter step : delegates) {
            intermediate = step.convert(intermediate);
        }
        return (T) intermediate;
    }

    @Override
    public Class<S> expectedSourceType() {
        return source;
    }

    @Override
    public Class<T> targetType() {
        return target;
    }

    /**
     * Instance that calculates a "route" through a number of converters using Dijkstra's algorithm.
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
                for (ContentTypeConverter candidate : new HashSet<>(candidates)) {
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
            for (ContentTypeConverter converter : new HashSet<>(candidates)) {
                if (sourceType.equals(converter.expectedSourceType())) {
                    Route route = new Route(converter);
                    if (route.endPoint().equals(targetType)) {
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
            return routes.get(0);
        }
    }

    private static final class Route {

        private final ContentTypeConverter<?, ?>[] nodes;
        private final Class endPoint;

        private Route(ContentTypeConverter initialVertex) {
            this.nodes = new ContentTypeConverter[]{initialVertex};
            endPoint = initialVertex.targetType();
        }

        private Route(ContentTypeConverter[] baseNodes, ContentTypeConverter newDestination) {
            nodes = Arrays.copyOf(baseNodes, baseNodes.length + 1);
            nodes[baseNodes.length] = newDestination;
            endPoint = newDestination.targetType();
        }

        private Route joinedWith(ContentTypeConverter newVertex) {
            Assert.isTrue(endPoint.equals(newVertex.expectedSourceType()),
                          () -> "Cannot append a vertex if it does not start where the current Route ends");
            return new Route(nodes, newVertex);
        }

        private Class<?> endPoint() {
            return endPoint;
        }

        private List<ContentTypeConverter<?,?>> asList() {
            return Arrays.asList(nodes);
        }
    }
}
