package org.axonframework.serializer;

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
 * A converter that delegates to a chain of other ContentTypeConverters to convert from a source to a target for which
 * there is not necessarily a single converter available.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class ChainedConverter<S, T> implements ContentTypeConverter<S, T> {

    private final List<ContentTypeConverter> delegates;
    private final Class<T> target;
    private final Class<S> source;

    /**
     * Returns a converter that can convert an IntermediateRepresentation from the given <code>sourceType</code> to the
     * given <code>targetType</code> using a chain formed with given <code>candidates</code>. The returned converter
     * uses some (or all) of the given <code>candidates</code> as delegates.
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
        Route route = new RouteCalculator(candidates).calculateRoute(sourceType, targetType);
        if (route == null) {
            throw new CannotConvertBetweenTypesException(format("Cannot build a converter to convert from %s to %s",
                                                                sourceType.getName(), targetType.getName()));
        }
        return new ChainedConverter<S, T>(route.asList());
    }

    /**
     * Creates a new instance that uses the given <code>delegates</code> to form a chain of converters. Note that the
     * <code>delegates</code> must for a Continuous chain, meaning that each item must produce an
     * IntermediateRepresentation of a type that can be consumed by the next delegate.
     * <p/>
     * To automatically calculate a route between converters, see {@link #calculateChain(Class source, Class target,
     * java.util.Collection candidates) calculateChain(source, target, candidates)}
     *
     * @param delegates the chain of delegates to perform the conversion
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public ChainedConverter(List<ContentTypeConverter> delegates) {
        Assert.isTrue(delegates != null && !delegates.isEmpty(), "The given delegates may not be null or empty");
        Assert.isTrue(isContinuous(delegates), "The given delegates must form a continuous chain");
        this.delegates = new ArrayList<ContentTypeConverter>(delegates);
        target = this.delegates.get(this.delegates.size() - 1).targetType();
        source = delegates.get(0).expectedSourceType();
    }

    private boolean isContinuous(List<ContentTypeConverter> delegates) {
        Class current = null;
        for (ContentTypeConverter delegate : delegates) {
            if (current == null || current.equals(delegate.expectedSourceType())) {
                current = delegate.targetType();
            } else {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public IntermediateRepresentation<T> convert(IntermediateRepresentation<S> original) {
        IntermediateRepresentation intermediate = original;
        for (ContentTypeConverter step : delegates) {
            intermediate = step.convert(intermediate);
        }
        return intermediate;
    }

    @Override
    public Class<S> expectedSourceType() {
        return source;
    }

    @Override
    public Class<T> targetType() {
        return target;
    }

    private static final class RouteCalculator {
        private final Set<ContentTypeConverter<?, ?>> candidates;
        private final List<Route> routes = new LinkedList<Route>();

        private RouteCalculator(Collection<ContentTypeConverter<?, ?>> candidates) {
            this.candidates = new HashSet<ContentTypeConverter<?, ?>>(candidates);
        }

        private Route calculateRoute(Class<?> sourceType, Class<?> targetType) {
            Route match = buildInitialRoutes(sourceType, targetType);
            if (match != null) {
                return match;
            }
            while (!candidates.isEmpty() && !routes.isEmpty()) {
                Route route = getShortestRoute();
                for (ContentTypeConverter candidate : candidates) {
                    if (route.endPoint().equals(candidate.expectedSourceType())) {
                        Route newRoute = route.joinedWith(candidate);
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
            List<ContentTypeConverter> candidatesToRemove = new ArrayList<ContentTypeConverter>();
            for (ContentTypeConverter converter : candidates) {
                if (sourceType.equals(converter.expectedSourceType())) {
                    Route route = new Route(converter);
                    if (route.endPoint().equals(targetType)) {
                        return route;
                    }
                    routes.add(route);
                    candidatesToRemove.add(converter);
                }
            }
            candidates.removeAll(candidatesToRemove);
            return null;
        }

        private Route getShortestRoute() {
            // since all nodes have equal distance, the first (i.e. oldest) node is the shortest
            return routes.get(0);
        }
    }

    private static final class Route {

        private final ContentTypeConverter[] nodes;
        private Class endPoint;

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
                          "Cannot append a vertex if it does not start where the current Route ends");
            return new Route(nodes, newVertex);
        }

        private Class<?> endPoint() {
            return endPoint;
        }

        private List<ContentTypeConverter> asList() {
            return Arrays.asList(nodes);
        }
    }
}
