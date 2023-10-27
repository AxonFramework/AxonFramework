/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.common.annotation;

import org.axonframework.commandhandling.RoutingKey;
import org.junit.jupiter.api.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.axonframework.common.annotation.AnnotationUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AnnotationUtils}
 *
 * @author Allard Buijze
 */
class AnnotationUtilsTest {

    @Test
    void findAttributesOnDirectAnnotation() throws NoSuchMethodException {
        Optional<Map<String, Object>> optionalResult =
                findAnnotationAttributes(getClass().getDeclaredMethod("directAnnotated"), TheTarget.class);
        assertTrue(optionalResult.isPresent());
        Map<String, Object> results = optionalResult.get();

        assertEquals("value", results.get("property"));
        assertFalse(results.containsKey("value"), "value property should use annotation Simple class name as key");
        assertEquals("value()", results.get("theTarget"));
    }

    @Test
    void findAttributesOnStaticMetaAnnotation() throws NoSuchMethodException {
        Optional<Map<String, Object>> optionalResult =
                findAnnotationAttributes(getClass().getDeclaredMethod("staticallyOverridden"), TheTarget.class);
        assertTrue(optionalResult.isPresent());
        Map<String, Object> results = optionalResult.get();

        assertEquals("overridden_statically", results.get("property"));
    }

    @Test
    void findAttributesOnDynamicMetaAnnotation() throws NoSuchMethodException {
        Optional<Map<String, Object>> optionalResult =
                findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), TheTarget.class);
        assertTrue(optionalResult.isPresent());
        Map<String, Object> results = optionalResult.get();

        assertEquals("dynamic-override", results.get("property"));
        assertEquals("extra", results.get("extraValue"));
    }

    @Test
    void findAttributesOnDynamicMetaAnnotationUsingAnnotationName() throws NoSuchMethodException {
        Optional<Map<String, Object>> optionalResult = findAnnotationAttributes(
                getClass().getDeclaredMethod("dynamicallyOverridden"), TheTarget.class.getName()
        );
        assertTrue(optionalResult.isPresent());
        Map<String, Object> results = optionalResult.get();

        assertEquals("dynamic-override", results.get("property"));
        assertEquals("extra", results.get("extraValue"));
        assertEquals("otherValue", results.get("theTarget"));
    }

    @Test
    void findAttributesOnNonExistentAnnotation() throws NoSuchMethodException {
        Optional<Map<String, Object>> result =
                findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), RoutingKey.class);
        assertFalse(result.isPresent(), "Didn't expect attributes to be found for non-existent annotation");
    }

    @Test
    void findAnnotationAttributesOnlyReturnsTargetAttributesAndOverridesForClassAnnotation()
            throws NoSuchMethodException {
        Method annotatedElement = getClass().getDeclaredMethod("dynamicallyOverridden");

        Map<String, Object> expected = new HashMap<>();
        expected.put("property", "dynamic-override");
        expected.put("theTarget", "otherValue");

        Optional<Map<String, Object>> result =
                findAnnotationAttributes(annotatedElement, TheTarget.class, OVERRIDE_ONLY);

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void findAnnotationAttributesOnlyReturnsTargetAttributesAndOverridesForStringAnnotation()
            throws NoSuchMethodException {
        Method annotatedElement = getClass().getDeclaredMethod("someAnnotatedMethod");

        Map<String, Object> expected = new HashMap<>();
        expected.put("property", "some-property");
        expected.put("theTarget", "otherValue");

        Optional<Map<String, Object>> result =
                findAnnotationAttributes(annotatedElement, TheTarget.class.getName(), OVERRIDE_ONLY);

        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void isAnnotatedWithReturnsTrue() {
        Set<Class<? extends Annotation>> expectedAnnotatedWithSubject = new HashSet<>();
        expectedAnnotatedWithSubject.add(DynamicOverrideAnnotated.class);
        expectedAnnotatedWithSubject.add(AnotherMetaAnnotation.class);
        Set<Class<? extends Annotation>> expectedVisited = new HashSet<>();
        expectedVisited.add(DynamicOverrideAnnotated.class);
        expectedVisited.add(AnotherMetaAnnotation.class);
        expectedVisited.add(TheTarget.class);
        expectedVisited.add(Retention.class);
        expectedVisited.add(Target.class);
        expectedVisited.add(Documented.class);

        Set<Class<? extends Annotation>> resultAnnotatedWithSubject = new HashSet<>();
        Set<Class<? extends Annotation>> resultVisited = new HashSet<>();
        boolean result = isAnnotatedWith(
                AnotherMetaAnnotation.class, TheTarget.class, resultAnnotatedWithSubject, resultVisited
        );

        assertTrue(result);
        assertEquals(expectedAnnotatedWithSubject, resultAnnotatedWithSubject);
        assertEquals(expectedVisited, resultVisited);
    }

    @Test
    void isAnnotatedWithReturnsFalse() {
        Set<Class<? extends Annotation>> expectedAnnotatedWithSubject = Collections.emptySet();
        Set<Class<? extends Annotation>> expectedVisited = new HashSet<>();
        expectedVisited.add(NotMetaAnnotated.class);
        expectedVisited.add(Documented.class);
        // Retention and Target are added as those are meta-annotations on Documented
        expectedVisited.add(Retention.class);
        expectedVisited.add(Target.class);

        Set<Class<? extends Annotation>> resultAnnotatedWithSubject = new HashSet<>();
        Set<Class<? extends Annotation>> resultVisited = new HashSet<>();
        boolean result = isAnnotatedWith(
                NotMetaAnnotated.class, TheTarget.class, resultAnnotatedWithSubject, resultVisited
        );

        assertFalse(result);
        assertEquals(expectedAnnotatedWithSubject, resultAnnotatedWithSubject);
        assertEquals(expectedVisited, resultVisited);
    }

    @TheTarget
    public void directAnnotated() {
    }

    @StaticOverrideAnnotated
    public void staticallyOverridden() {
    }

    @DynamicOverrideAnnotated(property = "dynamic-override")
    public void dynamicallyOverridden() {
    }

    @AnotherMetaAnnotation
    public void someAnnotatedMethod() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    public @interface TheTarget {

        String property() default "value";

        String value() default "value()";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @TheTarget(property = "overridden_statically")
    public @interface StaticOverrideAnnotated {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
    @TheTarget
    public @interface DynamicOverrideAnnotated {

        String property();

        String extraValue() default "extra";

        String theTarget() default "otherValue";
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @DynamicOverrideAnnotated(property = "some-property")
    public @interface AnotherMetaAnnotation {

    }

    @Documented
    public @interface NotMetaAnnotated {

    }
}
