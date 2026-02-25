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

import jakarta.annotation.Nonnull;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.annotation.Internal;
import org.axonframework.test.FixtureExecutionException;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility method dedicated towards resolving the {@link ProvidedAxonTestFixture}.
 *
 * @author Jan Galinski
 * @since 5.0.3
 */
@Internal final class ProvidedAxonTestFixtureUtils {

    static Optional<AxonTestFixtureProvider> findProvider(Object testInstance,
                                                          Class<?> testClass,
                                                          AnnotatedElement element) {
        Optional<Map<String, Object>> attrsOpt =
                AnnotationUtils.findAnnotationAttributes(element, ProvidedAxonTestFixture.class);

        if (attrsOpt.isEmpty() && element instanceof Method method) {
            attrsOpt = AnnotationUtils.findAnnotationAttributes(
                    method.getDeclaringClass(), ProvidedAxonTestFixture.class
            );
        }

        return attrsOpt.map(attrs -> {
                           Class<?> providerClass = (Class<?>) attrs.get("providedAxonTestFixture");
                           if (providerClass == null) {
                               providerClass = (Class<?>) attrs.get("value");
                           }
                           if (providerClass != null && providerClass != AxonTestFixtureProvider.class) {
                               validateValueUsage(element);
                               return Optional.of(instantiate((Class<? extends AxonTestFixtureProvider>) providerClass));
                           }
                           return getAxonTestFixtureProvider(testInstance, testClass);
                       })
                       .flatMap(opt -> opt)
                       .or(() -> getAxonTestFixtureProvider(testInstance, testClass));
    }

    private static void validateValueUsage(AnnotatedElement element) {
        if (element instanceof Field field) {
            if (AxonTestFixtureProvider.class.isAssignableFrom(field.getType())) {
                throw new FixtureExecutionException(
                        "When using @ProvidedAxonTestFixture on a field of type AxonTestFixtureProvider, " +
                                "it is forbidden to set the value."
                );
            }
        }
        if (element instanceof Method method) {
            if (AxonTestFixtureProvider.class.isAssignableFrom(method.getReturnType())
                    && method.getParameterCount() == 0) {
                throw new FixtureExecutionException(
                        "When using @ProvidedAxonTestFixture on a method of type AxonTestFixtureProvider, " +
                                "it is forbidden to set the value."
                );
            }
        }
    }

    private static AxonTestFixtureProvider instantiate(@Nonnull Class<? extends AxonTestFixtureProvider> clazz) {
        try {
            var constructor = ReflectionUtils.ensureAccessible(clazz.getDeclaredConstructor());
            return constructor.newInstance();
        } catch (Exception e) {
            throw new FixtureExecutionException(e.getMessage(), e);
        }
    }

    @Nonnull
    private static Optional<AxonTestFixtureProvider> getAxonTestFixtureProvider(Object testInstance,
                                                                                Class<?> testClass) {
        Optional<AxonTestFixtureProvider> onFields = findOnFields(testInstance, testClass);
        Optional<AxonTestFixtureProvider> onMethods = findOnMethods(testInstance, testClass);
        if (onFields.isPresent() && onMethods.isPresent()) {
            throw new FixtureExecutionException(
                    "Ambiguous @ProvidedAxonTestFixture configuration. Found both a field and a method provider."
            );
        }
        return onFields.or(() -> onMethods);
    }

    private static Optional<AxonTestFixtureProvider> findOnFields(Object testInstance, Class<?> testClass) {
        List<Field> annotatedFields = new ArrayList<>();
        for (Field field : ReflectionUtils.fieldsOf(testClass)) {
            if (AnnotationUtils.isAnnotationPresent(field, ProvidedAxonTestFixture.class)) {
                if (AxonTestFixtureProvider.class.isAssignableFrom(field.getType())) {
                    annotatedFields.add(field);
                }
            }
        }
        if (annotatedFields.size() > 1) {
            throw new FixtureExecutionException(
                    "Ambiguous @ProvidedAxonTestFixture configuration. Found multiple fields: " + annotatedFields
            );
        }
        return annotatedFields.isEmpty() ? Optional.empty() : getFieldValue(annotatedFields.getFirst(), testInstance);
    }

    private static Optional<AxonTestFixtureProvider> getFieldValue(Field field, Object instance) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new FixtureExecutionException("Static field providers are not supported. Found: " + field);
        }

        Object target = findTarget(field.getDeclaringClass(), instance);
        if (target == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ReflectionUtils.getFieldValue(field, target));
    }

    private static Optional<AxonTestFixtureProvider> findOnMethods(Object testInstance, Class<?> testClass) {
        List<Method> annotatedMethods = new ArrayList<>();
        for (Method method : ReflectionUtils.methodsOf(testClass)) {
            if (AnnotationUtils.isAnnotationPresent(method, ProvidedAxonTestFixture.class)) {
                if (AxonTestFixtureProvider.class.isAssignableFrom(method.getReturnType())
                        && method.getParameterCount() == 0) {
                    annotatedMethods.add(method);
                }
            }
        }
        if (annotatedMethods.size() > 1) {
            throw new FixtureExecutionException(
                    "Ambiguous @ProvidedAxonTestFixture configuration. Found multiple methods: " + annotatedMethods
            );
        }
        return annotatedMethods.isEmpty() ? Optional.empty() : invokeMethod(annotatedMethods.getFirst(), testInstance);
    }

    private static Optional<AxonTestFixtureProvider> invokeMethod(Method method, Object instance) {
        Object target = Modifier.isStatic(method.getModifiers())
                ? null : findTarget(method.getDeclaringClass(), instance);
        if (target == null && !Modifier.isStatic(method.getModifiers())) {
            return Optional.empty();
        }
        return Optional.ofNullable(ReflectionUtils.invokeAndGetMethodValue(method, target));
    }

    private static Object findTarget(Class<?> declaringClass, Object instance) {
        if (instance == null) {
            return null;
        }
        if (declaringClass.isInstance(instance)) {
            return instance;
        }
        // Try to find outer instance
        for (Field field : ReflectionUtils.fieldsOf(instance.getClass())) {
            if (field.getName().startsWith("this$")) {
                Object outer = ReflectionUtils.getFieldValue(field, instance);
                Object target = findTarget(declaringClass, outer);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    private ProvidedAxonTestFixtureUtils() {
        // Utility class
    }
}
