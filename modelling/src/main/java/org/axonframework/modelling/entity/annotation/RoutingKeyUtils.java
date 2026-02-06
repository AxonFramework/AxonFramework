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
import org.axonframework.common.annotation.Internal;

import java.lang.reflect.AnnotatedElement;
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
}
