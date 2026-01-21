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

package org.axonframework.test.extension;

import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Annotation to mark a field or method that provides the {@link AxonTestFixture}.
 * <p>
 * This annotation can be used in two ways:
 * <ul>
 *     <li>On a <b>field or method</b> (can be static) of a test class. The type or return type must be an
 *     {@link AxonTestFixtureProvider}. In this case, setting the {@link #value()} to an explicit provider implementation
 *     is forbidden as the annotated element already serves as the provider.</li>
 *     <li>On a <b>{@code @Test} method directly, or on the test class</b> itself (applying to all tests in that class).
 *     If used this way, the {@link #value()} <b>must</b> be set to a concrete {@link AxonTestFixtureProvider} implementation
 *     that the extension will use to instantiate the fixture, unless an {@link AxonTestFixtureProvider} is already
 *     declared via a field or method within the test class hierarchy.</li>
 * </ul>
 *
 * @author Jan Galinski
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProvidedAxonTestFixture {

    /**
     * The {@link AxonTestFixtureProvider} implementation to use when this annotation is placed on a test class or test
     * method.
     * <p>
     * Defaults to {@link AxonTestFixtureProvider} itself, indicating that the provider should be discovered on a field
     * or method.
     *
     * @return the provider implementation class
     */
    Class<? extends AxonTestFixtureProvider> value() default AxonTestFixtureProvider.class;
}

final class ProvidedAxonTestFixtureUtils {

    static Optional<AxonTestFixtureProvider> findProvider(Object testInstance, Class<?> testClass,
                                                         AnnotatedElement element) {
        Optional<ProvidedAxonTestFixture> annotation = findAnnotation(element);
        if (annotation.isEmpty()) {
            Optional<AxonTestFixtureProvider> onFields = findOnFields(testInstance, testClass);
            Optional<AxonTestFixtureProvider> onMethods = findOnMethods(testInstance, testClass);
            if (onFields.isPresent() && onMethods.isPresent()) {
                throw new FixtureExecutionException(
                        "Ambiguous @ProvidedAxonTestFixture configuration. Found both a field and a method provider."
                );
            }
            return onFields.or(() -> onMethods);
        }

        return annotation.flatMap(ann -> {
            if (ann.value() != AxonTestFixtureProvider.class) {
                validateValueUsage(element);
                return instantiate(ann.value());
            }
            Optional<AxonTestFixtureProvider> onFields = findOnFields(testInstance, testClass);
            Optional<AxonTestFixtureProvider> onMethods = findOnMethods(testInstance, testClass);
            if (onFields.isPresent() && onMethods.isPresent()) {
                throw new FixtureExecutionException(
                        "Ambiguous @ProvidedAxonTestFixture configuration. Found both a field and a method provider."
                );
            }
            return onFields.or(() -> onMethods);
        });
    }

    private static void validateValueUsage(AnnotatedElement element) {
        if (element instanceof Field) {
            Field field = (Field) element;
            if (AxonTestFixtureProvider.class.isAssignableFrom(field.getType())) {
                throw new FixtureExecutionException(
                        "When using @ProvidedAxonTestFixture on a field of type AxonTestFixtureProvider, " +
                                "it is forbidden to set the value."
                );
            }
        }
        if (element instanceof Method) {
            Method method = (Method) element;
            if (AxonTestFixtureProvider.class.isAssignableFrom(method.getReturnType())
                    && method.getParameterCount() == 0) {
                throw new FixtureExecutionException(
                        "When using @ProvidedAxonTestFixture on a method of type AxonTestFixtureProvider, " +
                                "it is forbidden to set the value."
                );
            }
        }
    }

    private static Optional<ProvidedAxonTestFixture> findAnnotation(AnnotatedElement element) {
        if (element == null) {
            return Optional.empty();
        }
        ProvidedAxonTestFixture ann = element.getAnnotation(ProvidedAxonTestFixture.class);
        if (ann != null) {
            return Optional.of(ann);
        }
        if (element instanceof Method) {
            return findAnnotation(((Method) element).getDeclaringClass());
        }
        if (element instanceof Field) {
            return findAnnotation(((Field) element).getDeclaringClass());
        }
        if (element instanceof Class) {
            Class<?> clazz = (Class<?>) element;
            // Check interfaces first if any
            for (Class<?> iface : clazz.getInterfaces()) {
                Optional<ProvidedAxonTestFixture> found = findAnnotation(iface);
                if (found.isPresent()) {
                    return found;
                }
            }
            // Check superclass
            Optional<ProvidedAxonTestFixture> found = findAnnotation(clazz.getSuperclass());
            if (found.isPresent()) {
                return found;
            }
            // Check enclosing class (for nested classes)
            return findAnnotation(clazz.getEnclosingClass());
        }
        return Optional.empty();
    }

    private static Optional<AxonTestFixtureProvider> findOnFields(Object testInstance, Class<?> testClass) {
        List<Field> annotatedFields = new ArrayList<>();
        for (Field field : getAllFields(testClass)) {
            if (field.isAnnotationPresent(ProvidedAxonTestFixture.class)) {
                if (AxonTestFixtureProvider.class.isAssignableFrom(field.getType())) {
                    annotatedFields.add(field);
                }
            }
        }
        if (annotatedFields.size() > 1) {
            throw new FixtureExecutionException("Ambiguous @ProvidedAxonTestFixture configuration. Found multiple fields: " + annotatedFields);
        }
        return annotatedFields.isEmpty() ? Optional.empty() : getFieldValue(annotatedFields.get(0), testInstance);
    }

    private static Optional<AxonTestFixtureProvider> findOnMethods(Object testInstance, Class<?> testClass) {
        List<Method> annotatedMethods = new ArrayList<>();
        for (Method method : getAllMethods(testClass)) {
            if (method.isAnnotationPresent(ProvidedAxonTestFixture.class)) {
                if (AxonTestFixtureProvider.class.isAssignableFrom(method.getReturnType())
                        && method.getParameterCount() == 0) {
                    annotatedMethods.add(method);
                }
            }
        }
        if (annotatedMethods.size() > 1) {
            throw new FixtureExecutionException("Ambiguous @ProvidedAxonTestFixture configuration. Found multiple methods: " + annotatedMethods);
        }
        return annotatedMethods.isEmpty() ? Optional.empty() : invokeMethod(annotatedMethods.get(0), testInstance);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        if (clazz.getEnclosingClass() != null) {
            List<Field> outerFields = getAllFields(clazz.getEnclosingClass());
            for (Field outerField : outerFields) {
                if (!fields.contains(outerField)) {
                    fields.add(outerField);
                }
            }
        }
        return fields;
    }

    private static List<Method> getAllMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        if (clazz.getEnclosingClass() != null) {
            List<Method> outerMethods = getAllMethods(clazz.getEnclosingClass());
            for (Method outerMethod : outerMethods) {
                if (!methods.contains(outerMethod)) {
                    methods.add(outerMethod);
                }
            }
        }
        return methods;
    }

    private static Optional<AxonTestFixtureProvider> getFieldValue(Field field, Object instance) {
        try {
            field.setAccessible(true);
            Object target = Modifier.isStatic(field.getModifiers()) ? null : findTarget(field.getDeclaringClass(), instance);
            if (target == null && !Modifier.isStatic(field.getModifiers())) {
                return Optional.empty();
            }
            Object value = field.get(target);
            return Optional.ofNullable((AxonTestFixtureProvider) value);
        } catch (IllegalAccessException e) {
            return Optional.empty();
        }
    }

    private static Optional<AxonTestFixtureProvider> invokeMethod(Method method, Object instance) {
        try {
            method.setAccessible(true);
            Object target = Modifier.isStatic(method.getModifiers()) ? null : findTarget(method.getDeclaringClass(), instance);
            if (target == null && !Modifier.isStatic(method.getModifiers())) {
                return Optional.empty();
            }
            Object value = method.invoke(target);
            return Optional.ofNullable((AxonTestFixtureProvider) value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Object findTarget(Class<?> declaringClass, Object instance) {
        if (instance == null) {
            return null;
        }
        if (declaringClass.isInstance(instance)) {
            return instance;
        }
        // Try to find outer instance
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().startsWith("this$")) {
                    try {
                        field.setAccessible(true);
                        Object outer = field.get(instance);
                        Object target = findTarget(declaringClass, outer);
                        if (target != null) {
                            return target;
                        }
                    } catch (IllegalAccessException e) {
                        // ignore
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Optional<AxonTestFixtureProvider> instantiate(Class<? extends AxonTestFixtureProvider> clazz) {
        try {
            return Optional.of(clazz.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ProvidedAxonTestFixtureUtils() {
        // Utility class
    }
}
