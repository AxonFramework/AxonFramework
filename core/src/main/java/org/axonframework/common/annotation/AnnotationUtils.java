/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.annotation;

import org.axonframework.common.AxonConfigurationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Utility class for locating annotations and attribute values on elements.
 *
 * @since 3.0
 * @author Allard Buijze
 */
public abstract class AnnotationUtils {

    private AnnotationUtils() {
        // utility class
    }

    /**
     * Find an annotation of given <code>annotationType</code> on the given <code>element</code>, considering that the
     * given <code>annotationType</code> may be present as a meta annotation on any other annotation on that element.
     *
     * @param <T>            The generic type of the annotation
     * @param element        The element to inspect
     * @param annotationType The type of annotation to find
     * @return an optional that contains the annotation if it was found on the element.
     */
    public static <T extends Annotation> Optional<T> findAnnotation(AnnotatedElement element, Class<T> annotationType) {
        T ann = element.getAnnotation(annotationType);
        if (ann == null) {
            HashSet<Class<? extends Annotation>> visited = new HashSet<>();
            for (Annotation metaAnn : element.getAnnotations()) {
                ann = getAnnotation(metaAnn.annotationType(), annotationType, visited);
                if (ann != null) {
                    break;
                }
            }
        }
        return Optional.ofNullable(ann);
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
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element, String annotationName) {
        Map<String, Object> attributes = new HashMap<>();
        Annotation ann = getAnnotation(element, annotationName);
        boolean found = false;
        if (ann != null) {
            collectAttributes(ann, attributes);
            found = true;
        } else {
            HashSet<String> visited = new HashSet<>();
            for (Annotation metaAnn : element.getAnnotations()) {
                if (collectAnnotationAttributes(metaAnn.annotationType(), annotationName, visited, attributes)) {
                    found = true;
                    collectAttributes(metaAnn, attributes);
                }
            }
        }
        return found ? Optional.of(attributes) : Optional.empty();
    }

    /**
     * Find the attributes of an annotation of type {@code annotationType} on the given {@code element}. The returned
     * optional has a value present if the annotation has been found, either directly on the {@code element}, or as a
     * meta-annotation.
     * <p>
     * The map of attributes contains all the attributes found on the annotation, as well as attributes of any
     * annotations on which the targeted annotation was placed (directly, or indirectly).
     *
     * @param element        The element for find the annotation on
     * @param annotationType The type of the annotation to find
     * @return an optional that resolved to a map with attribute names and value, if the annotation is found
     */
    public static Optional<Map<String, Object>> findAnnotationAttributes(AnnotatedElement element,
                                                                         Class<? extends Annotation> annotationType) {
        return findAnnotationAttributes(element, annotationType.getName());
    }

    private static boolean collectAnnotationAttributes(Class<? extends Annotation> target, String annotationType, HashSet<String> visited, Map<String, Object> attributes) {
        Annotation ann = getAnnotation(target, annotationType);
        if (ann == null && visited.add(target.getName())) {
            for (Annotation metaAnn : target.getAnnotations()) {
                if (collectAnnotationAttributes(metaAnn.annotationType(), annotationType, visited, attributes)) {
                    collectAttributes(metaAnn, attributes);
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
        Method[] methods = ann.annotationType().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class) {
                try {
                    Object value = method.invoke(ann);
                    attributes.put(method.getName(), value);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new AxonConfigurationException("Error while inspecting annotation values", e);
                }
            }
        }
    }

    private static <T extends Annotation> T getAnnotation(Class<? extends Annotation> target, Class<T> annotationType,
                                                          Set<Class<? extends Annotation>> visited) {
        T ann = target.getAnnotation(annotationType);
        if (ann == null && visited.add(target)) {
            for (Annotation metaAnn : target.getAnnotations()) {
                ann = getAnnotation(metaAnn.annotationType(), annotationType, visited);
                if (ann != null) {
                    break;
                }
            }
        }
        return ann;
    }
}
