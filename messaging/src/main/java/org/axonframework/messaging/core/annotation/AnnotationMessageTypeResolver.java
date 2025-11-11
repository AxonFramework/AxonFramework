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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link MessageTypeResolver} implementation looking for a configurable annotation on the {@link Class} this resolver
 * is invoked for.
 * <p>
 * Defaults to the {@link Message} annotation, resulting in a {@link MessageType}
 * based on the {@link Message#name() name} and {@link Message#version()} attributes of the annotation. When another
 * annotation is desired, a custom {@link AnnotationSpecification} can be given during construction of this resolver.
 * <p>
 * Allows for defining a fallback {@code MessageTypeResolver}, for when the defined annotation is not present.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AnnotationMessageTypeResolver implements MessageTypeResolver {

    private final MessageTypeResolver fallback;
    private final AnnotationSpecification specification;

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the {@link ClassBasedMessageTypeResolver} as
     * the fallback when there is no {@link Message} present on the given {@code payloadType} when resolving the
     * {@link MessageType}.
     * <p>
     * annotation to derive the {@link Message#name() name} and {@link Message#version()} to set on the
     * {@link MessageType} to resolve.
     */
    public AnnotationMessageTypeResolver() {
        this(new ClassBasedMessageTypeResolver(), AnnotationSpecification.DEFAULT);
    }

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the given {@code fallback} when there is no
     * {@link Message} present on the given {@code payloadType} when resolving the {@link MessageType}.
     *
     * @param fallback The message type resolver to fall back to whenever the {@link Message} is not present on the
     *                 given {@code payloadType} when resolving the {@link MessageType}.
     */
    public AnnotationMessageTypeResolver(@Nullable MessageTypeResolver fallback) {
        this(fallback, AnnotationSpecification.DEFAULT);
    }

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the given {@code fallback} when there defined
     * {@link AnnotationSpecification#annotation()} in the given {@code specification} is not present on the given
     * {@code payloadType} when resolving the {@link MessageType}.
     *
     * @param fallback      The message type resolver to fall back to whenever the
     *                      {@link AnnotationSpecification#annotation()} as defined in the given {@code specification}
     *                      is not present on the given {@code payloadType} when resolving the {@link MessageType}.
     * @param specification The specification dictating what annotation to look for on the given {@code payloadType}
     *                      when resolving the {@link MessageType}.
     */
    public AnnotationMessageTypeResolver(@Nullable MessageTypeResolver fallback,
                                         @Nonnull AnnotationSpecification specification) {
        this.fallback = fallback;
        this.specification = Objects.requireNonNull(specification, "The annotation specification may not be null.");
    }

    @Override
    public Optional<MessageType> resolve(@Nonnull Class<?> payloadType) {
        return AnnotationUtils.findAnnotationAttributes(payloadType, specification.annotation())
                              .map(attributes -> new MessageType(
                                      ObjectUtils.getNonEmptyOrDefault((String) attributes.get(specification.namespaceAttribute()),
                                                                       payloadType.getPackageName()),
                                      ObjectUtils.getNonEmptyOrDefault((String) attributes.get(specification.nameAttribute()),
                                                                       payloadType.getSimpleName()),
                                      (String) attributes.get(specification.versionAttribute())
                              ))
                              .or(() -> fallback != null ? fallback.resolve(payloadType) : Optional.empty());
    }

    /**
     * An annotation specification unique to the {@link AnnotationMessageTypeResolver}.
     *
     * @param annotation         The annotation class to search for when {@link #resolve(Class)} is invoked.
     * @param nameAttribute      The attribute for the {@link MessageType#name()}, that should be present on the given
     *                           {@code annotation}.
     * @param versionAttribute   The attribute for the {@link MessageType#version()}, that should be present on the
     *                           given {@code annotation}.
     * @param namespaceAttribute The attribute for the {@link QualifiedName#namespace()} field of the
     *                           {@link QualifiedName} set in the resolved {@link MessageType}. Whenever {@code null},
     *                           the {@link #nameAttribute()} is used on its own.
     */
    public record AnnotationSpecification(@Nonnull Class<? extends Annotation> annotation,
                                          @Nonnull String nameAttribute,
                                          @Nonnull String versionAttribute,
                                          @Nullable String namespaceAttribute) {

        /**
         * The default {@link AnnotationSpecification} looking for the {@link Message} annotation.
         */
        public static final AnnotationSpecification DEFAULT =
                new AnnotationSpecification(Message.class, "name", "version", "namespace");
    }
}
