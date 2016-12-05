/*
 * Copyright (c) 2010-2016. Axon Framework
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
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.ReflectionUtils.*;

/**
 * RoutingStrategy that expects an {@link org.axonframework.commandhandling.TargetAggregateIdentifier}
 * annotation on the command message's payload. Commands are routed based on the identifier of the aggregate that they
 * target. This approach ensures that commands to be processed by the same aggregate are dispatched to the same node in
 * a DistributedCommandBus. See {@link org.axonframework.commandhandling.AnnotationCommandTargetResolver}
 * for more details.
 * <p/>
 * This class requires the returned Aggregate Identifiers to implement a proper {@link Object#toString()} method. An
 * inconsistent toString() method may result in different members using different routing keys for the same identifier.
 *
 * @author Allard Buijze
 * @see org.axonframework.commandhandling.AnnotationCommandTargetResolver
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingStrategy extends AbstractRoutingStrategy {

    private static final AggregateIdentifierResolver NO_RESOLVE = new AggregateIdentifierResolver((Method) null);
    private final Class<? extends Annotation> annotationType;
    private final Map<Class<?>, AggregateIdentifierResolver> resolverMap = new ConcurrentHashMap<>();

    /**
     * Initializes a Routing Strategy that fails when an incoming command does not define an AggregateIdentifier to
     * base the routing key on.
     */
    public AnnotationRoutingStrategy() {
        this(TargetAggregateIdentifier.class);
    }

    /**
     * Initializes a Routing Strategy that uses the given annotation to resolve the targeted identifier.
     *
     * @param annotationType The type of annotation marking the field or method providing the identifier of the
     *                       targeted aggregate.
     */
    public AnnotationRoutingStrategy(Class<? extends Annotation> annotationType) {
        this(annotationType, UnresolvedRoutingKeyPolicy.ERROR);
    }

    /**
     * Initializes a Routing Strategy that uses the given {@code unresolvedRoutingKeyPolicy} when an incoming
     * command does not define an AggregateIdentifier to base the routing key on.
     *
     * @param unresolvedRoutingKeyPolicy The policy indication what should be done when a Command does not contain
     *                                   information about the routing key to use.
     */
    public AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        this(TargetAggregateIdentifier.class, unresolvedRoutingKeyPolicy);
    }

    /**
     * Initializes a Routing Strategy that uses the given annotation to resolve the targeted identifier and the given
     * {@code unresolvedRoutingKeyPolicy} when an incoming command does not define an AggregateIdentifier to base
     * the routing key on.
     *
     * @param annotationType             The type of annotation marking the field or method providing the identifier of
     *                                   the targeted aggregate.
     * @param unresolvedRoutingKeyPolicy The policy indication what should be done when a Command does not contain
     *                                   information about the routing key to use.
     */
    public AnnotationRoutingStrategy(Class<? extends Annotation> annotationType,
                                     UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        super(unresolvedRoutingKeyPolicy);
        this.annotationType = annotationType;
    }

    @Override
    protected String doResolveRoutingKey(CommandMessage<?> command) {
        String aggregateIdentifier;
        try {
            aggregateIdentifier = findIdentifier(command);
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException("An exception occurred while extracting aggregate "
                                                         + "information form a command", e);
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException("The current security context does not allow extraction of "
                                                         + "aggregate information from the given command.", e);
        }
        return aggregateIdentifier != null ? aggregateIdentifier : null;
    }

    @SuppressWarnings("unchecked")
    private String findIdentifier(CommandMessage<?> command) throws InvocationTargetException, IllegalAccessException {
        return resolverMap.computeIfAbsent(command.getPayloadType(), this::createResolver)
                .identify(command.getPayload());
    }

    private AggregateIdentifierResolver createResolver(Class<?> type) {
        for (Method m : methodsOf(type)) {
            if (m.isAnnotationPresent(annotationType)) {
                ensureAccessible(m);
                return new AggregateIdentifierResolver(m);
            }
        }
        for (Field f : fieldsOf(type)) {
            if (f.isAnnotationPresent(annotationType)) {
                return new AggregateIdentifierResolver(f);
            }
        }
        return NO_RESOLVE;
    }

    private static final class AggregateIdentifierResolver {
        private final Method method;
        private final Field field;

        public AggregateIdentifierResolver(Method method) {
            this.method = method;
            this.field = null;
        }

        public AggregateIdentifierResolver(Field field) {
            this.method = null;
            this.field = field;
        }

        public String identify(Object command) throws InvocationTargetException, IllegalAccessException {
            if (method != null) {
                return (String) method.invoke(command);
            } else if (field != null) {
                return ReflectionUtils.getFieldValue(field, command);
            }
            return null;
        }
    }
}
