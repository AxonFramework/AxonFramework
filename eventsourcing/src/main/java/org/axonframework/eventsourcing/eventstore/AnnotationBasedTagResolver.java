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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import org.axonframework.common.Assert;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotations.EventTag;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link TagResolver} that processes {@link EventTag} annotations on fields and methods
 * of event payload objects to create {@link Tag} instances.
 *
 * @author Your Name
 * @since 5.0.0
 */
public class AnnotationBasedTagResolver implements TagResolver {

    @Override
    public Set<Tag> resolve(@Nonnull EventMessage<?> event) {
        Assert.notNull(event, () -> "Event cannot be null");
        Object payload = event.getPayload();

        Set<Tag> tags = new HashSet<>();
        tags.addAll(resolveFieldTags(payload));
        tags.addAll(resolveMethodTags(payload));

        return tags;
    }

    private Set<Tag> resolveFieldTags(Object payload) {
        return Arrays.stream(payload.getClass().getDeclaredFields())
                     .filter(field -> field.isAnnotationPresent(EventTag.class))
                     .map(field -> createTagFromField(field, payload))
                     .filter(Objects::nonNull)
                     .collect(Collectors.toSet());
    }

    private Tag createTagFromField(Field field, Object payload) {
        try {
            field.setAccessible(true);
            Object value = field.get(payload);
            if (value == null) {
                return null;
            }

            EventTag annotation = field.getAnnotation(EventTag.class);
            String key = annotation.key().isEmpty() ? field.getName() : annotation.key();
            return new Tag(key, value.toString());
        } catch (IllegalAccessException e) {
            throw new TagResolutionException("Failed to resolve tag from field: " + field.getName(), e);
        }
    }

    private Set<Tag> resolveMethodTags(Object payload) {
        return Arrays.stream(payload.getClass().getDeclaredMethods())
                     .filter(method -> method.isAnnotationPresent(EventTag.class))
                     .filter(this::isValidTagMethod)
                     .map(method -> createTagFromMethod(method, payload))
                     .filter(Objects::nonNull)
                     .collect(Collectors.toSet());
    }

    private boolean isValidTagMethod(Method method) {
        return method.getParameterCount() == 0
                && !void.class.equals(method.getReturnType());
    }

    private Tag createTagFromMethod(Method method, Object payload) {
        try {
            method.setAccessible(true);
            Object value = method.invoke(payload);
            if (value == null) {
                return null;
            }

            EventTag annotation = method.getAnnotation(EventTag.class);
            String key = annotation.key().isEmpty() ? method.getName() : annotation.key();
            return new Tag(key, value.toString());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new TagResolutionException("Failed to resolve tag from method: " + method.getName(), e);
        }
    }

    /**
     * Exception thrown when tag resolution fails.
     */
    public static class TagResolutionException extends RuntimeException {
        public TagResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}