/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.junit.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import static org.junit.Assert.*;

public class AnnotationUtilsTest {

    @Test
    public void testFindAttributesOnDirectAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = AnnotationUtils.findAnnotationAttributes(getClass().getDeclaredMethod("directAnnotated"), TheTarget.class).get();

        assertEquals("value", results.get("property"));
        assertFalse("value property should use annotation Simple class name as key", results.containsKey("value"));
        assertEquals("value()", results.get("theTarget"));
    }

    @Test
    public void testFindAttributesOnStaticMetaAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = AnnotationUtils.findAnnotationAttributes(getClass().getDeclaredMethod("staticallyOverridden"), TheTarget.class).get();

        assertEquals("overridden_statically", results.get("property"));
    }

    @Test
    public void testFindAttributesOnDynamicMetaAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = AnnotationUtils.findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), TheTarget.class).get();

        assertEquals("dynamic-override", results.get("property"));
        assertEquals("extra", results.get("extraValue"));
    }

    @Test
    public void testFindAttributesOnDynamicMetaAnnotationUsingAnnotationName() throws NoSuchMethodException {
        Map<String, Object> results = AnnotationUtils.findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), TheTarget.class.getName()).get();

        assertEquals("dynamic-override", results.get("property"));
        assertEquals("extra", results.get("extraValue"));
        assertEquals("otherValue", results.get("theTarget"));
    }

    @Test
    public void testFindAttributesOnNonExistentAnnotation() throws NoSuchMethodException {
        assertFalse("Didn't expect attributes to be found for non-existent annotation",
                    AnnotationUtils.findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), RoutingKey.class).isPresent());
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

}
