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

package org.axonframework.messaging.core.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.ObjectUtils;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.StringUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.messaging.core.ClassBasedMessageTypeResolver;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.QualifiedName;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link MessageTypeResolver} implementation looking for configurable annotations on the {@link Class} this resolver
 * is invoked for.
 * <p>
 * This resolver can use unique annotations per attribute. The attributes searched for are the namespace, name, and
 * version, reflecting the {@link QualifiedName#namespace()}, {@link QualifiedName#localName()}, and
 * {@link MessageType#version()} to follow from resolution. The default annotation for the name and version is the
 * {@link Message} annotation, wherein the {@link Message#name() name} and {@link Message#version()} attributes will be
 * used respectively. The namespace attribute is uncovered through the {@link Namespace} annotation instead, with which
 * the {@code Message} annotation is meta-annotated as a fallback.
 * <p>
 * Allows for defining a fallback {@code MessageTypeResolver}, for when the defined annotations are not present.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AnnotationMessageTypeResolver implements MessageTypeResolver {

    private final MessageTypeResolver fallback;
    private final AnnotationSpecification specification;

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the {@link ClassBasedMessageTypeResolver} as
     * the fallback when there is no {@link Message} present on the given {@code payloadType} for resolving the name and
     * version specifically.
     */
    public AnnotationMessageTypeResolver() {
        this(new ClassBasedMessageTypeResolver(), AnnotationSpecification.DEFAULT);
    }

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the given {@code fallback} when there is no
     * {@link Message} present on the given {@code payloadType} for resolving the name and version specifically.
     *
     * @param fallback the message type resolver to fall back to whenever the {@link Message} is not present on the
     *                 given {@code payloadType} for resolving the name and version specifically
     */
    public AnnotationMessageTypeResolver(@Nullable MessageTypeResolver fallback) {
        this(fallback, AnnotationSpecification.DEFAULT);
    }

    /**
     * Constructs a default {@code AnnotationMessageTypeResolver}, using the given {@code fallback} when there defined
     * {@link AnnotationSpecification#nameAnnotation()} and {@link AnnotationSpecification#versionAnnotation()} in the
     * given {@code specification} are not present on the given {@code payloadType} when resolving the
     * {@link MessageType}.
     *
     * @param fallback      the message type resolver to fall back to whenever the
     *                      {@link AnnotationSpecification#nameAnnotation()} and
     *                      {@link AnnotationSpecification#versionAnnotation()} as defined in the given
     *                      {@code specification} are not present on the given {@code payloadType} when resolving the
     *                      {@link MessageType}
     * @param specification the specification dictating what annotation to look for on the given {@code payloadType}
     *                      when resolving the {@link MessageType}
     */
    public AnnotationMessageTypeResolver(@Nullable MessageTypeResolver fallback,
                                         @Nonnull AnnotationSpecification specification) {
        this.fallback = fallback;
        this.specification = Objects.requireNonNull(specification, "The annotation specification may not be null.");
    }

    @Override
    public Optional<MessageType> resolve(@Nonnull Class<?> payloadType) {
        Map<String, Object> nameAttributes = attributesFor(payloadType, specification.nameAnnotation());
        Map<String, Object> versionAttributes = attributesFor(payloadType, specification.versionAnnotation());
        if (nameAttributes.isEmpty() || versionAttributes.isEmpty()) {
            return fallback != null ? fallback.resolve(payloadType) : Optional.empty();
        }

        Map<String, Object> namespaceAttributes = namespaceAttributesFor(payloadType);
        return Optional.of(new MessageType(
                ObjectUtils.getNonEmptyOrDefault((String) namespaceAttributes.get(specification.namespaceAttribute()),
                                                 payloadType.getPackageName()),
                ObjectUtils.getNonEmptyOrDefault((String) nameAttributes.get(specification.nameAttribute()),
                                                 payloadType.getSimpleName()),
                (String) versionAttributes.get(specification.versionAttribute())
        ));
    }

    /**
     * This operation follows an ordering to search for the presence of a specific attribute on the given
     * {@code payloadType}.
     * <p>
     * It takes the following ordering:
     * <ol>
     *     <li>The type</li>
     *     <li>The enclosing types</li>
     *     <li>The package</li>
     *     <li>The module</li>
     * </ol>
     *
     * @param payloadType the payload type to search on for the {@link AnnotationSpecification#namespaceAnnotation()}
     * @return the namespace attributes, if any.
     */
    @Nonnull
    private Map<String, Object> namespaceAttributesFor(@Nonnull Class<?> payloadType) {
        // Look for class level annotation
        Map<String, Object> namespaceAttributes = attributesFor(payloadType, specification.namespaceAnnotation());

        // Look for enclosing class level annotation
        if (doesNotContainNamespaceAttribute(namespaceAttributes)) {
            Iterator<Class<?>> enclosingClasses = ReflectionUtils.enclosingClassesOf(payloadType).iterator();
            while (doesNotContainNamespaceAttribute(namespaceAttributes) && enclosingClasses.hasNext()) {
                namespaceAttributes = attributesFor(enclosingClasses.next(), specification.namespaceAnnotation());
            }
        }

        // Look for package level annotation
        if (doesNotContainNamespaceAttribute(namespaceAttributes)) {
            namespaceAttributes = attributesFor(payloadType.getPackage(), specification.namespaceAnnotation());
        }

        // Look for module level annotation
        if (doesNotContainNamespaceAttribute(namespaceAttributes)) {
            namespaceAttributes = attributesFor(payloadType.getModule(), specification.namespaceAnnotation());
        }

        return namespaceAttributes;
    }

    @Nonnull
    private Map<String, Object> attributesFor(@Nonnull AnnotatedElement annotatedElement,
                                              @Nonnull Class<? extends Annotation> annotation) {
        return AnnotationUtils.findAnnotationAttributes(annotatedElement, annotation)
                              .orElse(Collections.emptyMap());
    }

    private boolean doesNotContainNamespaceAttribute(Map<String, Object> namespaceAttributes) {
        return StringUtils.emptyOrNull((String) namespaceAttributes.get(specification.namespaceAttribute()));
    }

    /**
     * An annotation specification unique to the {@link AnnotationMessageTypeResolver}.
     *
     * @param nameAnnotation      the annotation class to search for when {@link #resolve(Class)} is invoked for the
     *                            {@code nameAttribute} specifically
     * @param nameAttribute       the attribute for the {@link MessageType#name()}, that should be present on the given
     *                            {@code annotation}
     * @param versionAnnotation   the annotation class to search for when {@link #resolve(Class)} is invoked for the
     *                            {@code versionAttribute} specifically
     * @param versionAttribute    the attribute for the {@link MessageType#version()}, that should be present on the
     *                            given {@code annotation}
     * @param namespaceAnnotation the annotation class to search for when {@link #resolve(Class)} is invoked for the
     *                            {@code namespaceAttribute} specifically
     * @param namespaceAttribute  the attribute for the {@link QualifiedName#namespace()} field of the
     *                            {@link QualifiedName} set in the resolved {@link MessageType}. Whenever {@code null},
     *                            the {@link #nameAttribute()} is used on its own
     */
    public record AnnotationSpecification(
            @Nonnull Class<? extends Annotation> nameAnnotation,
            @Nonnull String nameAttribute,
            @Nonnull Class<? extends Annotation> versionAnnotation,
            @Nonnull String versionAttribute,
            @Nonnull Class<? extends Annotation> namespaceAnnotation,
            @Nullable String namespaceAttribute
    ) {

        /**
         * The default {@link AnnotationSpecification} looking for the {@link Message} annotation for the
         * {@link #nameAttribute()} and {@link #versionAttribute()}, and the {@link Namespace} annotation for the
         * {@link #namespaceAttribute()}.
         */
        public static final AnnotationSpecification DEFAULT = new AnnotationSpecification(
                Message.class, "name", Message.class, "version", Namespace.class, "namespace"
        );

        /**
         * An annotation specification unique to the {@link AnnotationMessageTypeResolver}.
         *
         * @param annotation         the annotation class to search for when {@link #resolve(Class)} is invoked
         * @param nameAttribute      the attribute for the {@link MessageType#name()}, that should be present on the
         *                           given {@code annotation}
         * @param versionAttribute   the attribute for the {@link MessageType#version()}, that should be present on the
         *                           given {@code annotation}
         * @param namespaceAttribute the attribute for the {@link QualifiedName#namespace()} field of the
         *                           {@link QualifiedName} set in the resolved {@link MessageType}. Whenever
         *                           {@code null}, the {@link #nameAttribute()} is used on its own
         * @deprecated in favor of the {@link #AnnotationSpecification(Class, String, Class, String, Class, String)}
         * constructor that allows annotation configuration per resolvable attribute
         */
        @Deprecated(since = "5.1.0")
        public AnnotationSpecification(@Nonnull Class<? extends Annotation> annotation,
                                       @Nonnull String nameAttribute,
                                       @Nonnull String versionAttribute,
                                       @Nullable String namespaceAttribute) {
            this(annotation, nameAttribute, annotation, versionAttribute, annotation, namespaceAttribute);
        }
    }
}
