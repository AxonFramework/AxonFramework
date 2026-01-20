/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.entity.annotation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.annotation.Internal;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.axonframework.common.annotation.AnnotationUtils.findAnnotationAttributes;

/**
 * Utility class for retrieving routing keys from entity members and child entities.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class RoutingKeyUtils {

    private RoutingKeyUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Retrieves the routing key for the given member, which is defined by the {@link EntityMember#routingKey}
     * annotation.
     *
     * @param member The member to retrieve the routing key for.
     * @return An {@link Optional} containing the routing key if present, otherwise empty.
     */
    public static Optional<String> getMessageRoutingKey(@Nonnull AnnotatedElement member) {
        Objects.requireNonNull(member, "The member must not be null.");
        Optional<Map<String, Object>> attributes = findAnnotationAttributes(member, EntityMember.class);
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        String routingKeyProperty = (String) attributes.get().get("routingKey");
        if (!routingKeyProperty.isEmpty()) {
            return Optional.of(routingKeyProperty);
        }
        return Optional.empty();
    }

    /**
     * Retrieves the routing key property for the given child entity class, which is defined by the {@link RoutingKey}
     * annotation on one of its fields.
     *
     * @param childEntityClass The class of the child entity to retrieve the routing key property for.
     * @return An {@link Optional} containing the name of the routing key property if present, otherwise empty.
     */
    public static Optional<String> getEntityRoutingKey(@Nonnull Class<?> childEntityClass) {
        Objects.requireNonNull(childEntityClass, "The childEntityClass must not be null.");
        return Arrays.stream(childEntityClass.getDeclaredFields())
                     .filter(field -> AnnotationUtils.isAnnotationPresent(field, RoutingKey.class))
                     .findFirst()
                     .map(Field::getName);
    }
}
