/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.RoutingKey;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.axonframework.common.ReflectionUtils.*;

/**
 * RoutingStrategy that expects an {@link org.axonframework.commandhandling.RoutingKey} (meta-)annotation on the command
 * message's payload. Commands are routed based on an identifier annotated with the RoutingKey. This approach ensures
 * that commands with the same identifier, thus dealt with by the same target, are dispatched to the same node in
 * a {@link DistributedCommandBus}.
 * <p>
 * An example would be the {@link org.axonframework.commandhandling.TargetAggregateIdentifier}, which is annotated with
 * RoutingKey. See {@link org.axonframework.commandhandling.AnnotationCommandTargetResolver} for more details on this
 * approach.
 * <p/>
 * This class requires the returned routing keys to implement a proper {@link Object#toString()} method. An inconsistent
 * toString() method may result in different members using different routing keys for the same identifier.
 *
 * @author Allard Buijze
 * @see org.axonframework.commandhandling.TargetAggregateIdentifier
 * @see org.axonframework.commandhandling.AnnotationCommandTargetResolver
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingStrategy extends AbstractRoutingStrategy {

    private static final RoutingKeyResolver NO_RESOLVE = new RoutingKeyResolver((Method) null);

    private final Class<? extends Annotation> annotationType;
    private final Map<Class<?>, RoutingKeyResolver> resolverMap = new ConcurrentHashMap<>();

    /**
     * Initializes a Routing Strategy that fails when an incoming command does not define a field as the
     * {@link RoutingKey} to base the routing on.
     */
    public AnnotationRoutingStrategy() {
        this(RoutingKey.class);
    }

    /**
     * Initializes a Routing Strategy that uses the given annotation to resolve the targeted identifier.
     *
     * @param annotationType the type of annotation marking the field or method providing the identifier of the targeted
     *                       model
     */
    public AnnotationRoutingStrategy(Class<? extends Annotation> annotationType) {
        this(annotationType, UnresolvedRoutingKeyPolicy.ERROR);
    }

    /**
     * Initializes a Routing Strategy that uses the given {@code unresolvedRoutingKeyPolicy} when an incoming
     * command does not define a {@link RoutingKey} annotated field to base the routing on.
     *
     * @param unresolvedRoutingKeyPolicy a {@link UnresolvedRoutingKeyPolicy} indicating what should be done when a
     *                                   Command does not contain information about the routing key to use
     */
    public AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        this(RoutingKey.class, unresolvedRoutingKeyPolicy);
    }

    /**
     * Initializes a Routing Strategy that uses the given annotation to resolve the targeted identifier and the given
     * {@code unresolvedRoutingKeyPolicy} when an incoming command does not define an Identifier to base the routing key
     * on.
     *
     * @param annotationType             the type of annotation marking the field or method providing the identifier of
     *                                   the targeted model
     * @param unresolvedRoutingKeyPolicy a {@link UnresolvedRoutingKeyPolicy} indicating what should be done when a
     *                                   Command does not contain information about the routing key to use
     */
    public AnnotationRoutingStrategy(Class<? extends Annotation> annotationType,
                                     UnresolvedRoutingKeyPolicy unresolvedRoutingKeyPolicy) {
        super(unresolvedRoutingKeyPolicy);
        this.annotationType = annotationType;
    }

    @Override
    protected String doResolveRoutingKey(CommandMessage<?> command) {
        String routingKey;
        try {
            routingKey = findIdentifier(command);
        } catch (InvocationTargetException e) {
            throw new AxonConfigurationException(
                    "An exception occurred while extracting routing information form a command", e
            );
        } catch (IllegalAccessException e) {
            throw new AxonConfigurationException(
                    "The current security context does not allow extraction of routing information from the given command.",
                    e
            );
        }
        return routingKey;
    }

    @SuppressWarnings("unchecked")
    private String findIdentifier(CommandMessage<?> command) throws InvocationTargetException, IllegalAccessException {
        return resolverMap.computeIfAbsent(command.getPayloadType(), this::createResolver)
                          .identify(command.getPayload());
    }

    private RoutingKeyResolver createResolver(Class<?> type) {
        for (Method m : methodsOf(type)) {
            if (AnnotationUtils.findAnnotationAttributes(m, annotationType).isPresent()) {
                ensureAccessible(m);
                return new RoutingKeyResolver(m);
            }
        }
        for (Field f : fieldsOf(type)) {
            if (AnnotationUtils.findAnnotationAttributes(f, annotationType).isPresent()) {
                return new RoutingKeyResolver(f);
            }
        }
        return NO_RESOLVE;
    }

    private static final class RoutingKeyResolver {

        private final Method method;
        private final Field field;

        public RoutingKeyResolver(Method method) {
            this.method = method;
            this.field = null;
        }

        public RoutingKeyResolver(Field field) {
            this.method = null;
            this.field = field;
        }

        public String identify(Object command) throws InvocationTargetException, IllegalAccessException {
            if (method != null) {
                return Objects.toString(method.invoke(command));
            } else if (field != null) {
                return Objects.toString(ReflectionUtils.getFieldValue(field, command));
            }
            return null;
        }
    }
}
