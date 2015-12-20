/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.common;

import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.axonframework.common.ReflectionUtils.*;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 * @since 0.7
 */
public class ReflectionUtilsTest {

    @Test
    public void testFindFieldsInClass() {
        Iterable<Field> actualFields = ReflectionUtils.fieldsOf(SomeSubType.class);
        int t = 0;
        for (Field actual : actualFields) {
            if (actual.isSynthetic()) {
                // this test is probably running with ByteCode manipulation. We ignore synthetic fields.
                continue;
            }
            switch (t++) {
                case 0:
                    assertEquals("field3", actual.getName());
                    break;
                case 1:
                case 2:
                    assertTrue(
                            "Expected either field1 or field2, but got " + actual.getName()
                                    + " declared in " + actual.getDeclaringClass().getName(),
                            "field1".equals(actual.getName())
                                    || "field2".equals(actual.getName()));
                    break;
            }
        }
        assertTrue(t >= 2);
    }

    @Test
    public void testFindMethodsInClass() {
        Iterable<Method> actualMethods = ReflectionUtils.methodsOf(SomeSubType.class);
        int t = 0;
        for (Method actual : actualMethods) {
            if (actual.isSynthetic()) {
                //  this test is probably running with bytecode manipulation. We ignore synthetic methods.
                continue;
            }
            switch (t++) {
                case 0:
                    assertEquals("getField3", actual.getName());
                    break;
                case 1:
                    assertEquals("getField3", actual.getName());
                    assertEquals("SomeSubInterface", actual.getDeclaringClass().getSimpleName());
                    break;
                case 2:
                case 3:
                    assertTrue("Expected either getField1 or getField2, but got " + actual.getName()
                                       + " declared in " + actual.getDeclaringClass().getName(),
                               "getField1".equals(actual.getName())
                                       || "getField2".equals(actual.getName()));
                    break;
                case 4:
                    assertEquals("SomeInterface", actual.getDeclaringClass().getSimpleName());
                    break;
            }
        }
        assertTrue(t >= 4);
    }

    @Test
    public void testGetFieldValue() throws NoSuchFieldException {
        Object value = ReflectionUtils.getFieldValue(SomeType.class.getDeclaredField("field1"), new SomeSubType());
        assertEquals("field1", value);
    }

    @Test
    public void testIsAccessible() throws NoSuchFieldException {
        Field field1 = SomeType.class.getDeclaredField("field1");
        Field field2 = SomeType.class.getDeclaredField("field2");
        Field field3 = SomeSubType.class.getDeclaredField("field3");
        assertFalse(ReflectionUtils.isAccessible(field1));
        assertFalse(ReflectionUtils.isAccessible(field2));
        assertTrue(ReflectionUtils.isAccessible(field3));
    }

    @Test
    public void testExplicitlyUnequal_NullValues() {
        assertFalse(explicitlyUnequal(null, null));
        assertTrue(explicitlyUnequal(null, ""));
        assertTrue(explicitlyUnequal("", null));
    }

    @Test
    public void testHasEqualsMethod() {
        assertTrue(hasEqualsMethod(String.class));
        assertTrue(hasEqualsMethod(ArrayList.class));
        assertFalse(hasEqualsMethod(SomeType.class));
    }

    @SuppressWarnings("RedundantStringConstructorCall")
    @Test
    public void testExplicitlyUnequal_ComparableValues() {
        assertFalse(explicitlyUnequal("value", new String("value")));
        assertTrue(explicitlyUnequal("value1", "value2"));
    }

    @Test
    public void testExplicitlyUnequal_OverridesEqualsMethod() {
        assertFalse(explicitlyUnequal(Collections.singletonList("value"), Collections.singletonList("value")));
        assertTrue(explicitlyUnequal(Collections.singletonList("value1"), Collections.singletonList("value")));
    }

    @Test
    public void testExplicitlyUnequal_NoEqualsOrComparable() {
        assertFalse(explicitlyUnequal(new SomeType(), new SomeType()));
    }

    @Test
    public void testResolvePrimitiveWrapperTypeForLong() {
        assertEquals(Long.class, resolvePrimitiveWrapperType(long.class));
    }

    @Test
    public void testFindAttributesOnDirectAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = ReflectionUtils.findAnnotationAttributes(getClass().getDeclaredMethod("directAnnotated"), TheTarget.class);

        assertEquals("value", results.get("property"));
    }

    @Test
    public void testFindAttributesOnStaticMetaAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = ReflectionUtils.findAnnotationAttributes(getClass().getDeclaredMethod("staticallyOverridden"), TheTarget.class);

        assertEquals("overridden_statically", results.get("property"));
    }

    @Test
    public void testFindAttributesOnDynamicMetaAnnotation() throws NoSuchMethodException {
        Map<String, Object> results = ReflectionUtils.findAnnotationAttributes(getClass().getDeclaredMethod("dynamicallyOverridden"), TheTarget.class);

        assertEquals("dynamic-override", results.get("property"));
        assertEquals("extra", results.get("extraValue"));
    }

    @TheTarget
    public void directAnnotated() { }

    @StaticOverrideAnnotated
    public void staticallyOverridden() {

    }

    @DynamicOverrideAnnotated(property = "dynamic-override")
    public void dynamicallyOverridden() {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE,ElementType.METHOD})
    public @interface TheTarget {

        String property() default "value";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE,ElementType.METHOD})
    @TheTarget(property = "overridden_statically")
    public @interface StaticOverrideAnnotated {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE,ElementType.METHOD})
    @TheTarget
    public @interface DynamicOverrideAnnotated {

        String property();

        String extraValue() default "extra";
    }

    private static class SomeType implements SomeInterface  {

        private String field1 = "field1";
        private String field2 = "field2";

        @Override
        public String getField1() {
            return field1;
        }

        public String getField2() {
            return field2;
        }
    }

    public static interface SomeInterface {
        String getField1();
    }

    public static interface SomeSubInterface {
        int getField3();

    }

    public static class SomeSubType extends SomeType implements SomeSubInterface {

        public int field3 = 3;
        @Override
        public int getField3() {
            return field3;
        }

    }

    public static class ContainsCollectionsType extends SomeType {

        private List<String> listOfStrings;
        private Map<String, String> mapOfStringToString;
        private Set<String> setOfStrings;

        public ContainsCollectionsType(List<String> listOfStrings, Map<String, String> mapOfStringToString,
                                       Set<String> setOfStrings) {
            this.listOfStrings = listOfStrings;
            this.mapOfStringToString = mapOfStringToString;
            this.setOfStrings = setOfStrings;
        }

        public List<String> getListOfStrings() {
            return listOfStrings;
        }

        public Map<String, String> getMapOfStringToString() {
            return mapOfStringToString;
        }

        public Set<String> getSetOfStrings() {
            return setOfStrings;
        }
    }
}
