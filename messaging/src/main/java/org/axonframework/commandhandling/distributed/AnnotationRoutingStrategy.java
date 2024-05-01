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
import org.axonframework.commandhandling.annotation.RoutingKey;
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

import static org.axonframework.common.ReflectionUtils.*;

/**
 * RoutingStrategy that expects an {@link RoutingKey} (meta-)annotation on the command message's payload. Commands are
 * routed based on an identifier annotated with the {@code RoutingKey}. This approach ensures that commands with the
 * same identifier, thus dealt with by the same target, are dispatched to the same node in a distributed Command Bus
 * (e.g. {@code AxonServerCommandBus} or {@link DistributedCommandBus}).
 * <p>
 * An example would be the {@code TargetAggregateIdentifier} annotation, which is meta-annotated with
 * {@code RoutingKey}. See the AnnotationCommandTargetResolver for more details on this approach.
 * <p>
 * This class requires the returned routing keys to implement a proper {@link Object#toString()} method. An inconsistent
 * {@code toString()} method may result in different members using different routing keys for the same identifier.
 *
 * @author Allard Buijze
 * @see DistributedCommandBus
 * @since 2.0
 */
public class AnnotationRoutingStrategy implements RoutingStrategy {

    private static final RoutingKeyResolver NO_RESOLVE = new RoutingKeyResolver((Method) null);
    private static final String NULL_DEFAULT = null;

    private final Class<? extends Annotation> annotationType;
    private final Map<Class<?>, RoutingKeyResolver> resolverMap = new ConcurrentHashMap<>();

    /**
     * Instantiate a default {@link AnnotationRoutingStrategy}.
     * <p>
     * The {@code annotationType} is defaulted to {@link RoutingKey}.
     *
     * @return a default {@link AnnotationRoutingStrategy}
     */
    public AnnotationRoutingStrategy() {
        this(RoutingKey.class);
    }

    public AnnotationRoutingStrategy(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public String getRoutingKey(@Nonnull CommandMessage<?> command) {
        try {
            Object payload = command.getPayload();
            return payload == null ? null : findIdentifier(payload);
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
    }

    private String findIdentifier(Object payload) throws InvocationTargetException, IllegalAccessException {
        return resolverMap.computeIfAbsent(payload.getClass(), this::createResolver)
                          .identify(payload);
    }

    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
    private RoutingKeyResolver createResolver(Class<?> type) {
        for (Field f : fieldsOf(type)) {
            if (AnnotationUtils.findAnnotationAttributes(f, annotationType).isPresent()) {
                return new RoutingKeyResolver(f);
            }
        }
        for (Method m : methodsOf(type)) {
            if (AnnotationUtils.findAnnotationAttributes(m, annotationType).isPresent()) {
                ensureAccessible(m);
                return new RoutingKeyResolver(m);
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
}
