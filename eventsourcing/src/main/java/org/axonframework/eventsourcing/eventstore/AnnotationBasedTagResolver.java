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
import org.axonframework.common.ReflectionUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.annotations.EventTag;
import org.axonframework.eventsourcing.annotations.EventTags;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.getMemberValue;
import static org.axonframework.common.annotation.AnnotationUtils.*;

/**
 * Implementation of {@link TagResolver} that processes {@link EventTag} and {@link EventTags} annotations on fields and
 * methods of event payload objects to create {@link Tag} instances. Supports inherited fields and methods.
 * <p>
 * The {@link EventTag} annotation can be used in two ways:
 * <ul>
 *     <li>Directly on fields and methods using the repeatable syntax:
 *         <pre>
 *         {@literal @}EventTag
 *         {@literal @}EventTag(key = "identifier")
 *         private String id;
 *         </pre>
 *     </li>
 *     <li>Using the container annotation {@link EventTags}:
 *         <pre>
 *         {@literal @}EventTags({
 *             {@literal @}EventTag,
 *             {@literal @}EventTag(key = "identifier")
 *         })
 *         private String id;
 *         </pre>
 *     </li>
 * </ul>
 * Both approaches are equivalent and will produce the same tags.
 *
 * @author Mateusz Nowak
 * @see EventTag for more information on how to use the annotation
 * @see EventTags for more information on how to use the annotation
 * @since 5.0.0
 */
public class AnnotationBasedTagResolver implements TagResolver {

    private static final Class<EventTag> EVENT_TAG_ANNOTATION = EventTag.class;
    private static final Class<EventTags> CONTAINING_ANNOTATION_TYPE = EventTags.class;

    @Override
    public Set<Tag> resolve(@Nonnull EventMessage<?> event) {
        Objects.requireNonNull(event, "Event cannot be null");
        var payload = event.getPayload();
        return Stream.concat(resolveFieldTags(payload), resolveMethodTags(payload))
                     .collect(Collectors.toSet());
    }

    private Stream<Tag> resolveFieldTags(Object payload) {
        var fields = ReflectionUtils.fieldsOf(payload.getClass());
        return StreamSupport.stream(fields.spliterator(), false)
                            .filter(AnnotationBasedTagResolver::isTagAnnotationPresent)
                            .flatMap(field -> tagsFrom(field, payload).stream())
                            .filter(Objects::nonNull);
    }

    private Set<Tag> tagsFrom(Field field, Object payload) {
        try {
            var value = getMemberValue(field, payload);
            if (value == null) {
                return Set.of();
            }

            var tags = new HashSet<Tag>();
            var annotations = field.getAnnotationsByType(EVENT_TAG_ANNOTATION);

            for (var annotation : annotations) {
                tags.addAll(createTagsForValue(value, field.getName(), annotation.key()));
            }

            return tags;
        } catch (Exception e) {
            throw new TagResolutionException("Failed to resolve tag from field: " + field.getName(), e);
        }
    }

    private Stream<Tag> resolveMethodTags(Object payload) {
        var methods = ReflectionUtils.methodsOf(payload.getClass());
        return StreamSupport.stream(methods.spliterator(), false)
                            .filter(AnnotationBasedTagResolver::isTagAnnotationPresent)
                            .flatMap(field -> tagsFrom(field, payload).stream())
                            .filter(Objects::nonNull);
    }

    private static boolean isTagAnnotationPresent(AnnotatedElement member) {
        return isAnnotationPresent(member, EVENT_TAG_ANNOTATION)
                || isAnnotationPresent(member, CONTAINING_ANNOTATION_TYPE);
    }

    private Set<Tag> tagsFrom(Method method, Object payload) {
        assertValidTagMethod(method);
        try {
            var value = getMemberValue(method, payload);
            if (value == null) {
                return Set.of();
            }

            var tags = new HashSet<Tag>();
            var annotations = method.getAnnotationsByType(EVENT_TAG_ANNOTATION);

            for (var annotation : annotations) {
                tags.addAll(createTagsForValue(value, getMemberIdentifierName(method), annotation.key()));
            }

            return tags;
        } catch (Exception e) {
            throw new TagResolutionException("Failed to resolve tag from method: " + method.getName(), e);
        }
    }

    private Set<Tag> createTagsForValue(Object value, String memberName, String annotationKey) {
        if (value instanceof Iterable<?> iterable) {
            var key = annotationKeyOrMemberName(annotationKey, memberName);
            return StreamSupport.stream(iterable.spliterator(), false)
                                .filter(Objects::nonNull)
                                .map(item -> new Tag(key, item.toString()))
                                .collect(Collectors.toSet());
        }
        if (value instanceof Map<?, ?> map) {
            var annotationKeyProvided = !annotationKey.isBlank();
            if (annotationKeyProvided) {
                // If key provided in annotation, use it for all values
                return map.values().stream()
                          .filter(Objects::nonNull)
                          .map(val -> new Tag(annotationKey, val.toString()))
                          .collect(Collectors.toSet());
            }
            // If no key provided in annotation, use map keys regardless of property name
            return map.entrySet().stream()
                      .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                      .map(entry -> new Tag(entry.getKey().toString(), entry.getValue().toString()))
                      .collect(Collectors.toSet());
        }

        var key = annotationKeyOrMemberName(annotationKey, memberName);
        return Set.of(new Tag(key, value.toString()));
    }

    private static String annotationKeyOrMemberName(String annotationKey, String memberName) {
        return annotationKey.isBlank() ? memberName : annotationKey;
    }

    private void assertValidTagMethod(Method method) {
        if (method.getParameterCount() > 0) {
            throw new TagResolutionException(format(
                    "The @%s annotated method [%s] should not contain any parameters"
                            + " as none are allowed on event Tag providers",
                    EVENT_TAG_ANNOTATION.getSimpleName(), method
            ));
        }
        if (void.class.equals(method.getReturnType())) {
            throw new TagResolutionException(format(
                    "The @%s annotated method [%s] should not return void",
                    EVENT_TAG_ANNOTATION.getSimpleName(), method
            ));
        }
    }

    /**
     * Return the given {@code member}'s name. If the given {@code member} is of type {@link Method} and it resembles a
     * regular getter method, the {@code "get"} will be stripped off.
     *
     * @param member the {@link Member} to retrieve the name for
     * @return the identifier name tied to the given {@code member}
     */
    private String getMemberIdentifierName(Member member) {
        var identifierName = member.getName();
        return member instanceof Method && isGetterByConvention(identifierName)
                ? stripGetterConvention(identifierName)
                : identifierName;
    }

    private boolean isGetterByConvention(String identifierName) {
        return identifierName.startsWith("get")
                && identifierName.length() >= 4
                && Character.isUpperCase(identifierName.charAt(3));
    }

    private String stripGetterConvention(String identifierName) {
        return identifierName.substring(3, 4).toLowerCase() + identifierName.substring(4);
    }

    /**
     * Exception thrown when tag resolution fails.
     */
    public static class TagResolutionException extends RuntimeException {

        private TagResolutionException(String message) {
            super(message);
        }

        private TagResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}