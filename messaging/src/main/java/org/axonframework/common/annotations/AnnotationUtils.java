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

package org.axonframework.common.annotations;

import org.axonframework.common.AxonConfigurationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class for locating annotations and attribute values on elements.
 *
 * @author Allard Buijze
 * @since 3.0
 */
public final class AnnotationUtils {

    /**
     * Boolean specifying that a {@link #findAnnotationAttributes(AnnotatedElement, String, boolean)} invocation should
     * only contain the exact attributes of the target annotation, overridden by identical attributes on meta-annotated
     * annotations.
     */
    public static final boolean OVERRIDE_ONLY = true;
    /**
     * Boolean specifying that a {@link #findAnnotationAttributes(AnnotatedElement, String, boolean)} invocation should
     * contain all attributes of the target annotation recursively taken into account attributes of other annotations
     * the target is present on.
     */
    public static final boolean ADD_ALL = false;

    /**
     * Indicates whether an annotation of given {@code annotationType} is present on the given {@code element},
     * considering that the given {@code annotationType} may be present as a meta annotation on any other annotation on
     * that element.
     *
     * @param element        The element to inspect
     * @param annotationType The type of annotation to find
     * @return {@code true} if such annotation is present.
     */
    public static boolean isAnnotationPresent(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return isAnnotationPresent(element, annotationType.getName());
    }

    /**
     * Indicates whether an annotation with given {@code annotationType} is present on the given {@code element},
     * considering that the given {@code annotationType} may be present as a meta annotation on any other annotation on
     * that element.
     *
     * @param element        The element to inspect
     * @param annotationType The name of the annotation to find
     * @return {@code true} if such annotation is present.
     */
    public static boolean isAnnotationPresent(AnnotatedElement element, String annotationType) {
        return findAnnotationAttributes(element, annotationType).isPresent();
    }

    /**
     * Find the attributes of an annotation of type {@code annotationType} on the given {@code element}. The returned
     * optional has a value present if the annotation has been found, either directly on the {@code element}, or as a
     * meta-annotation.
     * <p>
     * The map of attributes contains all the attributes found on the annotation, as well as attributes of any
     * annotations on which the targeted annotation was placed (directly, or indirectly).
     * <p>
     * Note that the {@code value} property of annotations is reported as the simple class name (lowercase first
     * character) of the annotation. This allows specific attribute overrides for annotations that have multiple
     * meta-annotation with the {@code value} property.
     *
     * @param element        The element for find the annotation on
     * @param annotationType The type of the annotation to find
     * @return an optional that resolved to a map with attribute names and value, if the annotation is found
     */
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element,
                                                                         Class<? extends Annotation> annotationType) {
        return findAnnotationAttributes(element, annotationType, ADD_ALL);
    }

    /**
     * Find the attributes of an annotation of type {@code annotationType} on the given {@code element}. The returned
     * optional has a value present if the annotation has been found, either directly on the {@code element}, or as a
     * meta-annotation.
     * <p>
     * The map of attributes contains all the attributes found on the annotation. The {@code overrideOnly} parameter
     * defines whether all attributes of any annotation on which the targeted annotation was placed (directly, or
     * indirectly) should be included. For {@link #OVERRIDE_ONLY}, only attribute overrides will be added on top of
     * that, whereas for {@link #ADD_ALL} all attributes on any meta-annotated level will be included in the result.
     * <p>
     * Note that the {@code value} property of annotations is reported as the simple class name (lowercase first
     * character) of the annotation. This allows specific attribute overrides for annotations that have multiple
     * meta-annotation with the {@code value} property.
     *
     * @param element        the element for find the annotation on
     * @param annotationType the type of the annotation to find
     * @param overrideOnly   {@code boolean} defining whether or not to only take attribute overrides from
     *                       meta-annotations into account for the result or to include all attributes from every level
     * @return an optional that resolved to a map with attribute names and value, if the annotation is found
     */
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element,
                                                                         Class<? extends Annotation> annotationType,
                                                                         boolean overrideOnly) {
        return findAnnotationAttributes(element, annotationType.getName(), overrideOnly);
    }

    /**
     * Find the attributes of an annotation with given {@code annotationName} on the given {@code element}. The returned
     * optional has a value present if the annotation has been found, either directly on the {@code element}, or as a
     * meta-annotation.
     * <p>
     * The map of attributes contains all the attributes found on the annotation, as well as attributes of any
     * annotations on which the targeted annotation was placed (directly, or indirectly).
     *
     * @param element        The element for find the annotation on
     * @param annotationName The name of the annotation to find
     * @return an optional that resolved to a map with attribute names and value, if the annotation is found
     */
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element,
                                                                         String annotationName) {
        return findAnnotationAttributes(element, annotationName, ADD_ALL);
    }

    /**
     * Find the attributes of an annotation with given {@code annotationName} on the given {@code element}. The returned
     * optional has a value present if the annotation has been found, either directly on the {@code element}, or as a
     * meta-annotation.
     * <p>
     * The map of attributes contains all the attributes found on the annotation. The {@code overrideOnly} parameter
     * defines whether all attributes of any annotation on which the targeted annotation was placed (directly, or
     * indirectly) should be included. For {@link #OVERRIDE_ONLY}, only attribute overrides will be added on top of
     * that, whereas for {@link #ADD_ALL} all attributes on any meta-annotated level will be included in the result.
     * <p>
     * Note that the {@code value} property of annotations is reported as the simple class name (lowercase first
     * character) of the annotation. This allows specific attribute overrides for annotations that have multiple
     * meta-annotation with the {@code value} property.
     *
     * @param element        the element for find the annotation on
     * @param annotationName the name of the annotation to find
     * @param overrideOnly   {@code boolean} defining whether or not to only take attribute overrides from
     *                       meta-annotations into account for the result or to include all attributes from every level
     * @return an optional that resolved to a map with attribute names and value, if the annotation is found
     */
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element,
                                                                         String annotationName,
                                                                         boolean overrideOnly) {
        Map<String, Object> attributes = new HashMap<>();
        Annotation ann = getAnnotation(element, annotationName);
        boolean found = false;
        if (ann != null) {
            collectAttributes(ann, attributes);
            found = true;
        } else {
            HashSet<String> visited = new HashSet<>();
            for (Annotation metaAnn : element.getAnnotations()) {
                if (collectAnnotationAttributes(metaAnn.annotationType(),
                                                annotationName,
                                                visited,
                                                attributes,
                                                overrideOnly)) {
                    found = true;
                    collectAttributes(metaAnn, attributes, overrideOnly);
                }
            }
        }
        return found ? Optional.of(attributes) : Optional.empty();
    }

    /**
     * Find the {@code attributeName} of an annotation of type {@code annotationType} on the given {@code element}. The
     * returned optional has a value present if the annotation has been found, either directly on the {@code element},
     * or as a meta-annotation, <em>if</em> the named attribute exist.
     *
     * @param element        the element to find the annotation on
     * @param annotationType the type of the annotation to find
     * @param attributeName  the name of the attribute to find
     * @return an optional that resolved to the attribute value, if the annotation is found and if the attribute exists
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> findAnnotationAttribute(AnnotatedElement element,
                                                          Class<? extends Annotation> annotationType,
                                                          String attributeName) {
        return findAnnotationAttributes(element, annotationType.getName())
                .map(attributes -> attributes.get(attributeName))
                .map(attribute -> (T) attribute);
    }

    private static boolean collectAnnotationAttributes(Class<? extends Annotation> target,
                                                       String annotationType,
                                                       Set<String> visited,
                                                       Map<String, Object> attributes,
                                                       boolean overrideOnly) {
        Annotation ann = getAnnotation(target, annotationType);
        if (ann == null && visited.add(target.getName())) {
            for (Annotation metaAnn : target.getAnnotations()) {
                if (collectAnnotationAttributes(metaAnn.annotationType(),
                                                annotationType,
                                                visited,
                                                attributes,
                                                overrideOnly)) {
                    collectAttributes(metaAnn, attributes, overrideOnly);
                    return true;
                }
            }
        } else if (ann != null) {
            collectAttributes(ann, attributes);
            return true;
        }
        return false;
    }

    private static Annotation getAnnotation(AnnotatedElement target, String annotationType) {
        for (Annotation annotation : target.getAnnotations()) {
            if (annotationType.equals(annotation.annotationType().getName())) {
                return annotation;
            }
        }
        return null;
    }

    private static <T extends Annotation> void collectAttributes(T ann, Map<String, Object> attributes) {
        collectAttributes(ann, attributes, ADD_ALL);
    }

    private static <T extends Annotation> void collectAttributes(T ann,
                                                                 Map<String, Object> attributes,
                                                                 boolean overrideOnly) {
        Method[] methods = ann.annotationType().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                try {
                    String key = resolveName(method);
                    Object value = method.invoke(ann);
                    if (overrideOnly) {
                        attributes.computeIfPresent(key, (k, v) -> value);
                    } else {
                        attributes.put(key, value);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AxonConfigurationException("Error while inspecting annotation values", e);
                }
            }
        }
    }

    private static String resolveName(Method method) {
        if ("value".equals(method.getName())) {
            String simpleName = method.getDeclaringClass().getSimpleName();
            return simpleName.substring(0, 1).toLowerCase(Locale.ENGLISH).concat(simpleName.substring(1));
        }
        return method.getName();
    }

    /**
     * Validate whether the given {@code target} annotation {@link Class} is meta-annotated with the given {@code
     * subject}. If this is the case for the {@code target} itself or any meta-annotation on any level of the {@code
     * target}, {@code true} will be returned.
     * <p>
     * Any {@link Annotation} classes which are directly annotated or meta-annotated with the given {@code subject} will
     * be stored in the {@code annotatedWithSubject} {@link Set}. The {@code visited} {@code Set} is used to ignore
     * annotations which have already been validated.
     *
     * @param target               the annotation {@link Class} to validate if it is annotated with the given {@code
     *                             subject}
     * @param subject              the annotation {@link Class} to check whether it is present on the given {@code
     *                             target}, directly or through meta-annotations
     * @param annotatedWithSubject a {@link Set} to store all class' in which are annotated with the {@code subject},
     *                             either directly or through meta-annotations
     * @param visited              a {@link Set} containing all annotation class' which have been visited in the process
     *                             to overcome an endless validation loop
     * @return {@code true} if the {@code target} or any meta-annotations of the {@code target} are annotated with the
     * {@code subject}, {@code false} otherwise
     */
    public static boolean isAnnotatedWith(Class<? extends Annotation> target,
                                          Class<? extends Annotation> subject,
                                          Set<Class<? extends Annotation>> annotatedWithSubject,
                                          Set<Class<? extends Annotation>> visited) {
        boolean hasSubjectAnnotation = false;
        for (Annotation metaAnnotation : target.getAnnotations()) {
            if (subject.isAssignableFrom(metaAnnotation.annotationType())) {
                annotatedWithSubject.add(target);
                hasSubjectAnnotation = true;
            }
        }

        if (visited.add(target)) {
            for (Annotation metaAnnotation : target.getAnnotations()) {
                if (isAnnotatedWith(metaAnnotation.annotationType(), subject, annotatedWithSubject, visited)) {
                    annotatedWithSubject.add(target);
                    hasSubjectAnnotation = true;
                }
            }
        }

        return hasSubjectAnnotation;
    }

    private AnnotationUtils() {
        // Utility class
    }
}
