
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

package org.axonframework.common.infra;

import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite validating the common behavior of the {@link ComponentDescriptor}. While adding a new implementation you
 * should implement assertions with expected output for all the test cases.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public abstract class ComponentDescriptorTestSuite {

    protected ComponentDescriptor testSubject;

    abstract ComponentDescriptor testSubject();

    @BeforeEach
    void setUp() {
        testSubject = testSubject();
    }

    @Nested
    class NullTests {

        @Test
        void describeNullString() {
            // given
            String nullString = null;
            testSubject.describeProperty("nullString", nullString);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullString(result);
        }

        @Test
        void describeNullLong() {
            // given
            Long nullLong = null;
            testSubject.describeProperty("nullLong", nullLong);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullLong(result);
        }

        @Test
        void describeNullBoolean() {
            // given
            Boolean nullBoolean = null;
            testSubject.describeProperty("nullBoolean", nullBoolean);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullBoolean(result);
        }

        @Test
        void describeNullObject() {
            // given
            Object nullObject = null;
            testSubject.describeProperty("nullObject", nullObject);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullObject(result);
        }

        @Test
        void describeNullList() {
            // given
            List<?> nullList = null;
            testSubject.describeProperty("nullList", nullList);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullList(result);
        }

        @Test
        void describeNullMap() {
            // given
            Map<?, ?> nullMap = null;
            testSubject.describeProperty("nullMap", nullMap);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeNullMap(result);
        }
    }

    abstract void assertDescribeNullString(String result);

    abstract void assertDescribeNullLong(String result);

    abstract void assertDescribeNullBoolean(String result);

    abstract void assertDescribeNullObject(String result);

    abstract void assertDescribeNullMap(String result);

    abstract void assertDescribeNullList(String result);

    @Nested
    class PrimitivesTests {

        @Test
        void describeString() {
            // given
            testSubject.describeProperty("testName", "testValue");

            // when
            var result = testSubject.describe();

            // then
            assertDescribeString(result);
        }

        @Test
        void describeLong() {
            // given
            testSubject.describeProperty("testName", 42L);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeLong(result);
        }

        @Test
        void describeBoolean() {
            // given
            testSubject.describeProperty("testName", true);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeBoolean(result);
        }
    }

    abstract void assertDescribeBoolean(String result);

    abstract void assertDescribeLong(String result);

    abstract void assertDescribeString(String result);

    @Nested
    class CollectionTests {

        @Test
        void describeCollection() {
            // given
            var collection = List.of("value1", "value2", "value3");
            testSubject.describeProperty("collection", collection);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeCollection(result);
        }

        @Test
        void describeEmptyCollection() {
            // given
            var emptyCollection = List.<String>of();
            testSubject.describeProperty("emptyCollection", emptyCollection);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeEmptyCollection(result);
        }

        @Test
        void describeCollectionOfDescribableComponentsShouldIncludeTypeAndId() {
            // given
            var component1 = new SimpleTestComponent("component1", 101);
            var component2 = new SimpleTestComponent("component2", 102);
            var components = List.of(
                    component1,
                    component2
            );
            testSubject.describeProperty("components", components);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeCollectionOfDescribableComponentsShouldIncludeTypeAndId(result, component1, component2);
        }
    }

    abstract void assertDescribeCollectionOfDescribableComponentsShouldIncludeTypeAndId(String result,
                                                                                        SimpleTestComponent component1,
                                                                                        SimpleTestComponent component2);


    abstract void assertDescribeEmptyCollection(String result);

    abstract void assertDescribeCollection(String result);

    @Nested
    class MapTests {

        @Test
        void describeMap() {
            // given
            var map = Map.of(
                    "key1", "value1",
                    "key2", "value2"
            );
            testSubject.describeProperty("map", map);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeMap(result);
        }

        @Test
        void describeEmptyMap() {
            // given
            var emptyMap = Map.<String, String>of();
            testSubject.describeProperty("emptyMap", emptyMap);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeEmptyMap(result);
        }

        @Test
        void describeMapWithDescribableComponentsShouldIncludeTypeAndId() {
            // given
            var component1 = new SimpleTestComponent("value1", 201);
            var map = Map.of(
                    "component1", component1
            );
            testSubject.describeProperty("componentMap", map);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeMapWithDescribableComponentsShouldIncludeTypeAndId(result, component1);
        }
    }

    abstract void assertDescribeMapWithDescribableComponentsShouldIncludeTypeAndId(String result,
                                                                                   SimpleTestComponent component1);

    abstract void assertDescribeEmptyMap(String result);

    abstract void assertDescribeMap(String result);

    @Nested
    class ComponentTests {

        @Test
        void describeDescribableComponentShouldIncludeTypeAndId() {
            // given
            var component = new SimpleTestComponent("componentValue", 100);
            testSubject.describeProperty("component", component);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeDescribableComponentShouldIncludeTypeAndId(result, component);
        }
    }

    abstract void assertDescribeDescribableComponentShouldIncludeTypeAndId(String result,
                                                                           SimpleTestComponent component);

    @Nested
    class CircularReferencesTests {

        @Test
        void describeComponentWithCircularReference() {
            // given
            var component1 = new CircularComponent("Component1");
            var component2 = new CircularComponent("Component2");
            component1.setDependency(component2);
            component2.setDependency(component1);
            testSubject.describeProperty("circularRef", component1);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeComponentWithCircularReference(result, component1, component2);
        }

        @Test
        void describeComponentWithSelfReference() {
            // given
            var component = new CircularComponent("SelfReferencing");
            component.setDependency(component);
            testSubject.describeProperty("selfRef", component);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeComponentWithSelfReference(result, component);
        }

        @Test
        void describeCollectionWithCircularReferences() {
            // given
            var component1 = new CircularComponent("Component1");
            var component2 = new CircularComponent("Component2");
            component1.setDependency(component2);
            component2.setDependency(component1);

            var components = List.of(component1, component2);
            testSubject.describeProperty("circularRefCollection", components);

            // when
            var result = testSubject.describe();

            // then
            assertDescribeCollectionWithCircularReferences(result, component1, component2);
        }


        protected static class CircularComponent implements DescribableComponent {

            private final String name;

            private CircularComponent dependency;

            CircularComponent(String name) {
                this.name = name;
            }

            void setDependency(CircularComponent dependency) {
                this.dependency = dependency;
            }

            @Override
            public void describeTo(@Nonnull ComponentDescriptor descriptor) {
                descriptor.describeProperty("name", name);
                if (dependency != null) {
                    descriptor.describeProperty("dependency", dependency);
                }
            }
        }
    }

    abstract void assertDescribeCollectionWithCircularReferences(String result,
                                                                 CircularReferencesTests.CircularComponent component1,
                                                                 CircularReferencesTests.CircularComponent component2);

    abstract void assertDescribeComponentWithSelfReference(String result,
                                                           CircularReferencesTests.CircularComponent component);

    abstract void assertDescribeComponentWithCircularReference(String result,
                                                               CircularReferencesTests.CircularComponent component1,
                                                               CircularReferencesTests.CircularComponent component2);

    protected static int identityOf(Object component) {
        return System.identityHashCode(component);
    }

    protected record SimpleTestComponent(String name, int value) implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("value", value);
        }
    }
}