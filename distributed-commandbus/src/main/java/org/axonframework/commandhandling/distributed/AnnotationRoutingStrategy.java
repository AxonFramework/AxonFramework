/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.AxonConfigurationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

/**
 * RoutingStrategy that by default expects an {@link org.axonframework.commandhandling.annotation.TargetAggregateIdentifier}
 * annotation on the command message's payload. Commands are routed based on the identifier of the aggregate that they
 * target. This approach ensures that commands to be processed by the same aggregate are dispatched to the same node in
 * a DistributedCommandBus. See {@link org.axonframework.commandhandling.annotation.AnnotationCommandTargetResolver}
 * for more details.
 * <p/>
 * This class requires the returned Aggregate Identifiers to implement a proper {@link Object#toString()} method. An
 * inconsistent toString() method may result in different members using different routing keys for the same identifier.
 *
 * It is also possible to supply a {@link AnnotationRoutingResolver} configured with user defined annotations.
 * In this way it is possible to create Command types decoupled from the Axon API that can still participate
 * in this RoutingStrategy.
 *
 * @author Allard Buijze
 * @see org.axonframework.commandhandling.annotation.AnnotationCommandTargetResolver
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingStrategy extends AbstractRoutingStrategy {

    private AnnotationRoutingResolver annotationRoutingResolver;

    /**
     * Initializes a Routing Strategy that fails when an incoming command does not define an AggregateIdentifier to
     * base the routing key on.
     */
    public AnnotationRoutingStrategy() {
        this(new AnnotationRoutingResolver(), UnresolvedRoutingKeyPolicy.ERROR);
    }

    /**
     * Initializes a Routing Strategy that uses the given <code>unresolvedRoutingKeyPolicy</code> when an incoming
     * command does not define an AggregateIdentifier to base the routing key on.
     *
     * @param unresolvedRoutingKeyPolicy The policy indication what should be done when a Command does not contain
     *                                   information about the routing key to use.
     */

    public AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        this(new AnnotationRoutingResolver(), unresolvedRoutingKeyPolicy);
    }

    /**
     * Initializes a Routing Strategy that checks for one of the candidate aggregate types to determine and
     * fails when an incoming command does not define an AggregateIdentifier to base the routing key on.
     *
     * @param candidateAnnotationTypes annotation types that can mark methods or fields that will provide the AggregateIdentifier
     */
    public AnnotationRoutingStrategy(Class<? extends Annotation>... candidateAnnotationTypes) {
        this(new AnnotationRoutingResolver(candidateAnnotationTypes), UnresolvedRoutingKeyPolicy.ERROR);
    }


    /**
     * Initializes a Routing Strategy that uses the given <code>annotationRoutingResolver</code> to determine
     * the AggregateIdentifier and the give <code>unresolvedRoutingKeyPolicy</code> when an incoming
     * command does not define an AggregateIdentifier to base the routing key on.
     *
     * @param annotationRoutingResolver The resolver used to determine the routing key
     * @param unresolvedRoutingKeyPolicy The policy indication what should be done when a Command does not contain
     *                                   information about the routing key to use.
     */
    public AnnotationRoutingStrategy(AnnotationRoutingResolver annotationRoutingResolver,
                                     UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        super(unresolvedRoutingKeyPolicy);
        this.annotationRoutingResolver = annotationRoutingResolver;
    }

    /**
     * Returns a Routing Strategy that checks for one of the candidate annotation types.
     * The result for each Command type is cached.
     *
     * @param candidateAnnotationTypes annotation types that will be considered
     * @return a AnnotationRoutingStrategy that caches the result for each Command type
     */
    public static AnnotationRoutingStrategy withCacheing(Class<? extends Annotation>... candidateAnnotationTypes) {
        return new AnnotationRoutingStrategy(AnnotationRoutingResolver.withCacheing(candidateAnnotationTypes), UnresolvedRoutingKeyPolicy.ERROR);
    }

    @Override
    protected String doResolveRoutingKey(CommandMessage<?> command) {
        Object aggregateIdentifier;
        try {
            aggregateIdentifier = findIdentifier(command);
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException("An exception occurred while extracting aggregate "
                                                         + "information form a command", e);
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException("The current security context does not allow extraction of "
                                                         + "aggregate information from the given command.", e);
        }
        return aggregateIdentifier != null ? aggregateIdentifier.toString() : null;
    }

    private Object findIdentifier(CommandMessage<?> command) throws InvocationTargetException, IllegalAccessException {
        RoutingStrategy routingStrategy = annotationRoutingResolver.getRoutingStrategy(command.getPayloadType());
        if (routingStrategy != null) {
            return routingStrategy.getRoutingKey(command);
        } else {
            return null;
        }
    }
}
