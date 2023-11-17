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
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.ReflectionUtils.*;

/**
 * RoutingStrategy that expects an {@link RoutingKey} (meta-)annotation on the command message's payload. Commands are
 * routed based on an identifier annotated with the {@code RoutingKey}. This approach ensures that commands with the
 * same identifier, thus dealt with by the same target, are dispatched to the same node in a distributed Command Bus
 * (e.g. {@code AxonServerCommandBus} or {@link DistributedCommandBus}).
 * <p>
 * An example would be the {@code TargetAggregateIdentifier} annotation, which is meta-annotated with {@code
 * RoutingKey}. See the AnnotationCommandTargetResolver for more details on this approach.
 * <p>
 * This class requires the returned routing keys to implement a proper {@link Object#toString()} method. An inconsistent
 * {@code toString()} method may result in different members using different routing keys for the same identifier.
 *
 * @author Allard Buijze
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingStrategy extends AbstractRoutingStrategy {

    private static final RoutingKeyResolver NO_RESOLVE = new RoutingKeyResolver((Method) null);
    private static final String NULL_DEFAULT = null;

    private final Class<? extends Annotation> annotationType;
    private final Map<Class<?>, RoutingKeyResolver> resolverMap = new ConcurrentHashMap<>();

    /**
     * Instantiate a Builder to be able to create a {@link AnnotationRoutingStrategy}.
     * <p>
     * The {@code annotationType} is defaulted to {@link RoutingKey} and the {@code fallbackRoutingStrategy} to {@link
     * UnresolvedRoutingKeyPolicy#RANDOM_KEY}.
     *
     * @return a Builder to be able to create a {@link AnnotationRoutingStrategy}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a default {@link AnnotationRoutingStrategy}.
     * <p>
     * The {@code annotationType} is defaulted to {@link RoutingKey} and the {@code fallbackRoutingStrategy} to {@link
     * UnresolvedRoutingKeyPolicy#RANDOM_KEY}.
     *
     * @return a default {@link AnnotationRoutingStrategy}
     */
    public static AnnotationRoutingStrategy defaultStrategy() {
        return builder().build();
    }

    /**
     * Instantiate a {@link AnnotationRoutingStrategy} based on the fields contained in the given {@code builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AnnotationRoutingStrategy} instance
     */
    protected AnnotationRoutingStrategy(Builder builder) {
        super(builder.fallbackRoutingStrategy);
        builder.validate();
        this.annotationType = builder.annotationType;
    }

    @Override
    protected String doResolveRoutingKey(@Nonnull CommandMessage<?> command) {
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

    private String findIdentifier(CommandMessage<?> command) throws InvocationTargetException, IllegalAccessException {
        return resolverMap.computeIfAbsent(command.getPayloadType(), this::createResolver)
                          .identify(command.getPayload());
    }

    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
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
                return Objects.toString(method.invoke(command), NULL_DEFAULT);
            } else if (field != null) {
                return Objects.toString(ReflectionUtils.getFieldValue(field, command), NULL_DEFAULT);
            }
            return null;
        }
    }

    /**
     * Builder class to instantiate a {@link AnnotationRoutingStrategy}.
     * <p>
     * The {@code annotationType} is defaulted to {@link RoutingKey} and the {@code fallbackRoutingStrategy} to {@link
     * UnresolvedRoutingKeyPolicy#RANDOM_KEY}.
     */
    public static class Builder {

        private Class<? extends Annotation> annotationType = RoutingKey.class;
        private RoutingStrategy fallbackRoutingStrategy = UnresolvedRoutingKeyPolicy.RANDOM_KEY;

        /**
         * Sets the fallback {@link RoutingStrategy} to use when the intended routing key resolution was unsuccessful.
         * Defaults to a {@link UnresolvedRoutingKeyPolicy#RANDOM_KEY}
         *
         * @param fallbackRoutingStrategy a {@link RoutingStrategy} used as the fallback whenever the intended routing
         *                                key resolution was unsuccessful
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder fallbackRoutingStrategy(RoutingStrategy fallbackRoutingStrategy) {
            assertNonNull(fallbackRoutingStrategy, "Fallback RoutingStrategy may not be null");
            this.fallbackRoutingStrategy = fallbackRoutingStrategy;
            return this;
        }

        /**
         * Sets the {@code annotationType} {@link Class} searched for by this routing strategy on a {@link
         * CommandMessage} to base the routing key on. Defaults to the {@link RoutingKey} annotation.
         *
         * @param annotationType an annotation {@link Class} to search for on a {@link CommandMessage} which contains
         *                       the command's routing key
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder annotationType(Class<? extends Annotation> annotationType) {
            assertNonNull(annotationType, "AnnotationType may not be null");
            this.annotationType = annotationType;
            return this;
        }

        /**
         * Initializes a {@link AnnotationRoutingStrategy} implementation as specified through this Builder.
         *
         * @return a {@link AnnotationRoutingStrategy} implementation as specified through this Builder
         */
        public AnnotationRoutingStrategy build() {
            return new AnnotationRoutingStrategy(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            // Nothing to validate due to defaults; kept for overriding
        }
    }
}
