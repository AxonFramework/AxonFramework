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

package org.axonframework.common;

import org.junit.jupiter.api.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.common.ReflectionUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ReflectionUtils}.
 *
 * @author Allard Buijze
 */
class ReflectionUtilsTest {

    @Test
    void findFieldsInClass() {
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
                    assertTrue("field1".equals(actual.getName()) || "field2".equals(actual.getName()),
                               "Expected either field1 or field2, but got " + actual.getName()
                                       + " declared in " + actual.getDeclaringClass().getName());
                    break;
            }
        }
        assertTrue(t >= 2);
    }

    @Test
    void nonRecursivelyFindFieldsInClass() {
        Iterable<Field> actualFields = ReflectionUtils.fieldsOf(SomeSubType.class, NOT_RECURSIVE);
        int t = 0;
        for (Field actual : actualFields) {
            if (actual.isSynthetic()) {
                // this test is probably running with ByteCode manipulation. We ignore synthetic fields.
                continue;
            }
            assertEquals("field3", actual.getName());
            t++;
        }
        assertTrue(t >= 1);
    }

    @Test
    void findMethodsInClass() {
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
                    assertTrue("getField1".equals(actual.getName()) || "getField2".equals(actual.getName()),
                               "Expected either getField1 or getField2, but got " + actual.getName()
                                       + " declared in " + actual.getDeclaringClass().getName());
                    break;
                case 4:
                    assertEquals("SomeInterface", actual.getDeclaringClass().getSimpleName());
                    break;
            }
        }
        assertTrue(t >= 4);
    }

    @Test
    void nonRecursivelyFindMethodsInClass() {
        Iterable<Method> actualMethods = ReflectionUtils.methodsOf(SomeSubType.class, NOT_RECURSIVE);
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
            }
        }
        assertTrue(t >= 2);
    }

    @Test
    void getFieldValue() throws NoSuchFieldException {
        Object value = ReflectionUtils.getFieldValue(SomeType.class.getDeclaredField("field1"), new SomeSubType());
        assertEquals("field1", value);
    }

    @Test
    void setFieldValue() throws Exception {
        int expectedFieldValue = 4;
        SomeSubType testObject = new SomeSubType();
        ReflectionUtils.setFieldValue(SomeSubType.class.getDeclaredField("field3"), testObject, expectedFieldValue);
        assertEquals(expectedFieldValue, testObject.getField3());
    }

    @SuppressWarnings("deprecation")
    @Test
    void isAccessible() throws NoSuchFieldException {
        Field field1 = SomeType.class.getDeclaredField("field1");
        Field field2 = SomeType.class.getDeclaredField("field2");
        Field field3 = SomeSubType.class.getDeclaredField("field3");
        assertFalse(ReflectionUtils.isAccessible(field1));
        assertFalse(ReflectionUtils.isAccessible(field2));
        assertTrue(ReflectionUtils.isAccessible(field3));
    }

    @Test
    void explicitlyUnequal_NullValues() {
        assertFalse(explicitlyUnequal(null, null));
        assertTrue(explicitlyUnequal(null, ""));
        assertTrue(explicitlyUnequal("", null));
    }

    @Test
    void hasEqualsMethodTest() {
        assertTrue(hasEqualsMethod(String.class));
        assertTrue(hasEqualsMethod(ArrayList.class));
        assertFalse(hasEqualsMethod(SomeType.class));
    }

    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    void explicitlyUnequal_ComparableValues() {
        assertFalse(explicitlyUnequal("value", new String("value")));
        assertTrue(explicitlyUnequal("value1", "value2"));
    }

    @Test
    void explicitlyUnequal_OverridesEqualsMethod() {
        assertFalse(explicitlyUnequal(Collections.singletonList("value"), Collections.singletonList("value")));
        assertTrue(explicitlyUnequal(Collections.singletonList("value1"), Collections.singletonList("value")));
    }

    @Test
    void explicitlyUnequal_NoEqualsOrComparable() {
        assertFalse(explicitlyUnequal(new SomeType(), new SomeType()));
    }

    @Test
    void resolvePrimitiveWrapperTypeForLong() {
        assertEquals(Long.class, resolvePrimitiveWrapperType(long.class));
    }

    @Test
    void getMemberValueFromField() throws NoSuchFieldException {
        assertEquals("field1",
                     ReflectionUtils.getMemberValue(SomeType.class.getDeclaredField("field1"), new SomeSubType()));
    }

    @Test
    void getMemberValueFromMethod() throws NoSuchMethodException {
        assertEquals("field1",
                     ReflectionUtils.getMemberValue(SomeType.class.getDeclaredMethod("getField1"), new SomeSubType()));
        assertEquals("someMethodResult",
                     ReflectionUtils.getMemberValue(SomeTypeWithMethods.class.getDeclaredMethod("someMethod"), new SomeTypeWithMethods()));
    }

    @Test
    void getMemberValueFromVoidMethod() throws NoSuchMethodException {
        SomeTypeWithMethods testObject = new SomeTypeWithMethods();
        Object voidReturnValue = getMemberValue(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"),
                                                testObject);
        assertNull(voidReturnValue);
        assertEquals(1, testObject.voidMethodInvocations.get());
    }

    @Test
    void getMemberValueFromConstructor() throws NoSuchMethodException {
        Constructor<SomeType> testConstructor = SomeType.class.getDeclaredConstructor();
        SomeSubType testTarget = new SomeSubType();
        assertThrows(IllegalStateException.class, () -> ReflectionUtils.getMemberValue(testConstructor, testTarget));
    }

    @Test
    void getMemberValueTypeFromField() throws NoSuchFieldException {
        Class<?> fieldType = getMemberValueType(SomeType.class.getDeclaredField("field1"));
        assertEquals(String.class, fieldType);
    }

    @Test
    void getMemberValueTypeFromMethod() throws NoSuchMethodException {
        Class<?> methodValueType = getMemberValueType(SomeSubType.class.getDeclaredMethod("getField3"));
        assertEquals(int.class, methodValueType);
        Class<?> voidResultType = getMemberValueType(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"));
        assertEquals(void.class, voidResultType);
    }

    @Test
    void getMemberValueTypeFromConstructor() throws NoSuchMethodException {
        Constructor<SomeType> testConstructor = SomeType.class.getDeclaredConstructor();
        assertThrows(IllegalStateException.class, () -> getMemberValueType(testConstructor));
    }

    @Test
    void invokeAndGetMethodValueTest() throws NoSuchMethodException {
        assertEquals("field1",
                     invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("getField1"), new SomeTypeWithMethods()));
        assertEquals("someMethodResult",
                     invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("someMethod"), new SomeTypeWithMethods()));

        SomeTypeWithMethods testObject = new SomeTypeWithMethods();
        Object voidMethodResult = invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"),
                                                          testObject);
        assertNull(voidMethodResult);
        assertEquals(1, testObject.voidMethodInvocations.get());
    }

    @Test
    void memberGenericTypeFromField() throws NoSuchFieldException {
        Field field = SomeType.class.getDeclaredField("field1");
        Type memberGenericType = getMemberGenericType(field);
        assertEquals(field.getGenericType(), memberGenericType);
    }

    @Test
    void memberGenericTypeFromMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getDeclaredMethod("someMethod");
        Type memberGenericType = getMemberGenericType(method);
        assertEquals(method.getGenericReturnType(), memberGenericType);
    }

    @Test
    void memberGenericTypeFromConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        assertThrows(IllegalStateException.class, () -> getMemberGenericType(constructor));
    }

    @Test
    void memberGenericStringFromField() throws NoSuchFieldException {
        Field field = SomeType.class.getDeclaredField("field1");
        String memberGenericString = getMemberGenericString(field);
        assertEquals(field.toGenericString(), memberGenericString);
    }

    @Test
    void memberGenericStringFromMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getDeclaredMethod("someMethod");
        String memberGenericString = getMemberGenericString(method);
        assertEquals(method.toGenericString(), memberGenericString);
    }

    @Test
    void memberGenericStringFromConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        String memberGenericString = getMemberGenericString(constructor);
        assertEquals(constructor.toGenericString(), memberGenericString);
    }


    @Test
    void toDiscernableSignatureOfConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        String memberGenericString = toDiscernibleSignature(constructor);
        assertEquals("org.axonframework.common.ReflectionUtilsTest$SomeTypeWithMethods()", memberGenericString);
    }

    @Test
    void toDiscernableSignatureOfMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getMethod("someMethodWithParameters", String.class, Integer.class, Object.class);
        String memberGenericString = toDiscernibleSignature(method);
        assertEquals("someMethodWithParameters(java.lang.String,java.lang.Integer,java.lang.Object)", memberGenericString);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private static class SomeType implements SomeInterface {

        private final String field1 = "field1";
        private final String field2 = "field2";

        @Override
        public String getField1() {
            return field1;
        }

        public String getField2() {
            return field2;
        }
    }

    public interface SomeInterface {

        @SuppressWarnings("unused")
        String getField1();
    }

    public interface SomeSubInterface {

        int getField3();
    }

    public static class SomeSubType extends SomeType implements SomeSubInterface {

        public int field3 = 3;

        @Override
        public int getField3() {
            return field3;
        }
    }

    private static class SomeTypeWithMethods implements SomeInterface {

        public AtomicInteger voidMethodInvocations = new AtomicInteger();

        @Override
        public String getField1() {
            return "field1";
        }

        public String someMethod() {
            return "someMethodResult";
        }

        public void someVoidMethod() {
            voidMethodInvocations.incrementAndGet();
        }

        public String someMethodWithParameters(String parameterOne, Integer parameterTwo, Object parameter3) {
            return "someMethodWithParametersResult";
        }
    }
}
