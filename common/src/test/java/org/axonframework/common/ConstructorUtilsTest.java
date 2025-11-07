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

package org.axonframework.common;

import org.junit.jupiter.api.*;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ConstructorUtilsTest {

    @Nested
    class OptionalArgument {

        @Test
        void canNotGetConstructorWithWrongArgumentType() {
            assertThrows(IllegalArgumentException.class,
                         () -> ConstructorUtils.factoryForTypeWithOptionalArgument(
                                 ClassWithOnlyIntegerConstructor.class,
                                 String.class
                         )
            );
        }

        @Test
        void canConstructObjectWithOneArgConstructorForInteger() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    ClassWithOnlyIntegerConstructor.class,
                    Integer.class
            );

            ClassWithOnlyIntegerConstructor instance1 = function.apply(1);
            ClassWithOnlyIntegerConstructor instance2 = function.apply(2);
            ClassWithOnlyIntegerConstructor instance3 = function.apply(3);

            assertEquals(1, instance1.getValue());
            assertEquals(2, instance2.getValue());
            assertEquals(3, instance3.getValue());
        }

        @Test
        void canConstructObjectWithOneArgConstructorForString() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    TestClassWithOnlyStringConstructor.class,
                    String.class
            );

            TestClassWithOnlyStringConstructor instance1 = function.apply("1");
            TestClassWithOnlyStringConstructor instance2 = function.apply("2");
            TestClassWithOnlyStringConstructor instance3 = function.apply("3");

            assertEquals("1", instance1.getValue());
            assertEquals("2", instance2.getValue());
            assertEquals("3", instance3.getValue());
        }

        @Test
        void canConstructObjectWithZeroArgumentEvenWithOptionalArgument() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    TestClassWithOnlyZeroArgConstructor.class,
                    String.class
            );

            TestClassWithOnlyZeroArgConstructor instance1 = function.apply("1");
            TestClassWithOnlyZeroArgConstructor instance2 = function.apply("2");
            TestClassWithOnlyZeroArgConstructor instance3 = function.apply("3");

            assertNotNull(instance1);
            assertNotNull(instance2);
            assertNotNull(instance3);

            assertNotSame(instance1, instance2);
            assertNotSame(instance1, instance3);
            assertNotSame(instance2, instance3);
        }

        @Test
        void fallsBackToNoArgConstructorIfTypeDoesNotMatch() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    ClassWithStringAndNoArgConstructor.class,
                    Integer.class
            );
            ClassWithStringAndNoArgConstructor instance1 = function.apply(1);
            ClassWithStringAndNoArgConstructor instance2 = function.apply(2);
            ClassWithStringAndNoArgConstructor instance3 = function.apply(3);

            assertNotNull(instance1);
            assertNotNull(instance2);
            assertNotNull(instance3);

            assertEquals("default", instance1.getValue());
            assertEquals("default", instance2.getValue());
            assertEquals("default", instance3.getValue());

            assertNotSame(instance1, instance2);
            assertNotSame(instance1, instance3);
            assertNotSame(instance2, instance3);
        }
    }


    @Nested
    class ZeroArgConstructor {

        @Test
        void canGetZeroArgConstructorAndUseItToConstructMultipleTimes() {
            Supplier<TestClassWithOnlyZeroArgConstructor> supplier = ConstructorUtils.getConstructorFunctionWithZeroArguments(
                    TestClassWithOnlyZeroArgConstructor.class
            );
            TestClassWithOnlyZeroArgConstructor instance1 = supplier.get();
            assertInstanceOf(TestClassWithOnlyZeroArgConstructor.class, instance1);
            TestClassWithOnlyZeroArgConstructor instance2 = supplier.get();
            assertInstanceOf(TestClassWithOnlyZeroArgConstructor.class, instance2);
            TestClassWithOnlyZeroArgConstructor instance3 = supplier.get();
            assertInstanceOf(TestClassWithOnlyZeroArgConstructor.class, instance3);

            assertNotSame(instance1, instance2);
            assertNotSame(instance1, instance3);
            assertNotSame(instance2, instance3);
        }

        @Test
        void canNotGetZeroArgConstructorIfItDoesntExist() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ConstructorUtils.getConstructorFunctionWithZeroArguments(
                            TestClassWithOnlyStringConstructor.class
                    )
            );
        }
    }


    @Nested
    class Records {


        @Test
        void canConstructRecordClassWithProvidedOptionalArgument() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    RecordClassWithOneArgConstructor.class,
                    String.class
            );
            assertNotNull(function);
            RecordClassWithOneArgConstructor instance1 = function.apply("test1");
            RecordClassWithOneArgConstructor instance2 = function.apply("test2");
            RecordClassWithOneArgConstructor instance3 = function.apply("test3");
            assertEquals("test1", instance1.value());
            assertEquals("test2", instance2.value());
            assertEquals("test3", instance3.value());
        }

        @Test
        void canNotConstructRecordClassWithTwoArgConstructor() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ConstructorUtils.factoryForTypeWithOptionalArgument(
                            RecordClassWithTwoArgConstructor.class,
                            String.class
                    )
            );
        }

        @Test
        void canConstructRecordWithOverloadedConstructorIfHasOneArgument() {
            var function = ConstructorUtils.factoryForTypeWithOptionalArgument(
                    RecordClassWithOverloadedConstructor.class,
                    Integer.class
            );

            assertNotNull(function);
            RecordClassWithOverloadedConstructor instance1 = function.apply(1);
            RecordClassWithOverloadedConstructor instance2 = function.apply(2);
            RecordClassWithOverloadedConstructor instance3 = function.apply(3);

            assertEquals("1", instance1.value());
            assertEquals(1, instance1.intValue());
            assertEquals("2", instance2.value());
            assertEquals(2, instance2.intValue());
            assertEquals("3", instance3.value());
            assertEquals(3, instance3.intValue());
        }


        public record RecordClassWithOneArgConstructor(String value) {

        }

        public record RecordClassWithTwoArgConstructor(String value, Integer intValue) {

        }

        public record RecordClassWithOverloadedConstructor(String value, Integer intValue) {

            public RecordClassWithOverloadedConstructor(Integer intValue) {
                this(String.valueOf(intValue), intValue);
            }
        }
    }

    // Shared Test classes
    @SuppressWarnings("ClassCanBeRecord")
    public static class TestClassWithOnlyStringConstructor {

        private final String value;

        public TestClassWithOnlyStringConstructor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @SuppressWarnings("ClassCanBeRecord")
    public static class ClassWithOnlyIntegerConstructor {

        private final Integer value;

        public ClassWithOnlyIntegerConstructor(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }

    public static class TestClassWithOnlyZeroArgConstructor {

        public TestClassWithOnlyZeroArgConstructor() {
        }
    }

    public static class ClassWithStringAndNoArgConstructor {

        private final String value;

        public ClassWithStringAndNoArgConstructor(String value) {
            this.value = value;
        }

        public ClassWithStringAndNoArgConstructor() {
            this.value = "default";
        }

        public String getValue() {
            return value;
        }
    }
}