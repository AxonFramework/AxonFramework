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

class JacksonComponentDescriptorTest {

    private JacksonComponentDescriptor testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new JacksonComponentDescriptor();
    }

    @Nested
    class PrimitivesTests {

        @Test
        void describeString() {
            // given
            testSubject.describeProperty("testName", "testValue");

            // when
            var result = testSubject.describe();

            // then
            assertJsonMatches(result, """
                    {
                      "testName" : "testValue"
                    }
                    """
            );
        }

        @Test
        void describeLong() {
            // given
            testSubject.describeProperty("testName", 42L);

            // when
            var result = testSubject.describe();

            // then
            assertJsonMatches(result, """
                    {
                      "testName" : 42
                    }
                    """
            );
        }

        @Test
        void describeBoolean() {
            // given
            testSubject.describeProperty("testName", true);

            // when
            var result = testSubject.describe();

            // then
            assertJsonMatches(result, """
                    {
                      "testName" : true
                    }
                    """);
        }
    }

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
            assertJsonMatches(result, """
                    {
                      "collection" : [ "value1", "value2", "value3" ]
                    }
                    """
            );
        }

        @Test
        void describeEmptyCollection() {
            // given
            var emptyCollection = List.<String>of();
            testSubject.describeProperty("emptyCollection", emptyCollection);

            // when
            var result = testSubject.describe();

            // then
            assertJsonMatches(result, """
                    {
                      "emptyCollection" : [ ]
                    }
                    """
            );
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
            assertJsonMatches(result, """
                    {
                      "components" : [ {
                        "_id" : "%s",
                        "_type" : "SimpleTestComponent",
                        "name" : "component1",
                        "value" : 101
                      }, {
                        "_id" : "%s",
                        "_type" : "SimpleTestComponent",
                        "name" : "component2",
                        "value" : 102
                      } ]
                    }
                    """.formatted(identityOf(component1), identityOf(component2))
            );
        }
    }

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
            // since maps don't guarantee order in JSON representation, we need to check both possibilities
            var expected1 = normalizeJson("""
                                                  {
                                                    "map" : {
                                                      "key1" : "value1",
                                                      "key2" : "value2"
                                                    }
                                                  }
                                                  """);
            var expected2 = normalizeJson("""
                                                  {
                                                    "map" : {
                                                      "key2" : "value2",
                                                      "key1" : "value1"
                                                    }
                                                  }
                                                  """);
            var normalizedResult = normalizeJson(result);
            assertTrue(
                    normalizedResult.equals(expected1) || normalizedResult.equals(expected2),
                    "Expected one of two possible formats but got: " + result
            );
        }

        @Test
        void describeEmptyMap() {
            // given
            var emptyMap = Map.<String, String>of();
            testSubject.describeProperty("emptyMap", emptyMap);

            // when
            var result = testSubject.describe();
            var expected = normalizeJson("""
                                                 {
                                                   "emptyMap" : { }
                                                 }
                                                 """);

            // then
            assertEquals(expected, normalizeJson(result));
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
            assertJsonMatches(result, """
                        {
                          "componentMap": {
                            "component1":{
                                "_id":"%s",
                                "_type":"SimpleTestComponent",
                                "name":"value1",
                                "value":201
                            }
                          }
                        }
                    """.formatted(identityOf(component1))
            );
        }
    }

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
            assertJsonMatches(result, """
                    {
                      "component" : {
                        "_id" : "%s",
                        "_type" : "SimpleTestComponent",
                        "name" : "componentValue",
                        "value" : 100
                      }
                    }
                    """.formatted(identityOf(component)));
        }

        @Test
        void describeComplexStructureShouldIncludeTypeAndId() {
            // given
            record ComplexTestComponent(
                    String stringValue,
                    long numberValue,
                    boolean booleanValue,
                    DescribableComponent component,
                    List<String> simpleList,
                    List<DescribableComponent> componentList,
                    Map<String, String> simpleMap
            ) implements DescribableComponent {

                @Override
                public void describeTo(@Nonnull ComponentDescriptor descriptor) {
                    descriptor.describeProperty("stringValue", stringValue);
                    descriptor.describeProperty("numberValue", numberValue);
                    descriptor.describeProperty("booleanValue", booleanValue);
                    descriptor.describeProperty("component", component);
                    descriptor.describeProperty("simpleList", simpleList);
                    descriptor.describeProperty("componentList", componentList);
                    descriptor.describeProperty("simpleMap", simpleMap);
                }
            }

            var structure = new ComplexTestComponent(
                    "test",
                    42L,
                    true,
                    new SimpleTestComponent("nestedComponent", 300),
                    List.of("listItem1", "listItem2"),
                    List.of(
                            new SimpleTestComponent("listComponent1", 301),
                            new SimpleTestComponent("listComponent2", 302)
                    ),
                    Map.of("mapKey1", "mapValue1")
            );

            testSubject.describeProperty("complexStructure", structure);

            // when
            var result = testSubject.describe();

            // then
            assertJsonMatchesPattern(
                    result,
                    """
                            {
                                "complexStructure":{
                                    "_id":"*",
                                    "_type":"ComplexTestComponent",
                                    "stringValue":"test",
                                    "numberValue":42,
                                    "booleanValue":true,
                                    "component":{
                                        "_id":"*",
                                        "_type":"SimpleTestComponent",
                                        "name":"nestedComponent",
                                        "value":300
                                    },
                                    "simpleList":["listItem1","listItem2"],
                                    "componentList":[
                                        {
                                            "_id":"*",
                                            "_type":"SimpleTestComponent",
                                            "name":"listComponent1",
                                            "value":301
                                        },
                                        {
                                            "_id":"*",
                                            "_type":"SimpleTestComponent",
                                            "name":"listComponent2",
                                            "value":302
                                        }
                                    ],
                                    "simpleMap":{"mapKey1":"mapValue1"}
                                }
                            }
                            """
            );
        }
    }

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
            assertJsonMatches(result, """
                    {
                      "circularRef": {
                        "_id": "%s",
                        "_type": "CircularComponent",
                        "name": "Component1",
                        "dependency": {
                          "_id": "%s",
                          "_type": "CircularComponent",
                          "name": "Component2",
                          "dependency": {
                            "$ref": "%s",
                            "_type": "CircularComponent" 
                          }
                        }
                      }
                    }
                    """.formatted(identityOf(component1), identityOf(component2), identityOf(component1))
            );
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
            assertJsonMatches(result, """
                    {
                      "selfRef": {
                        "_id": "%s",
                        "_type": "CircularComponent",
                        "name": "SelfReferencing",
                        "dependency": {
                          "$ref": "%s",
                          "_type": "CircularComponent"
                        }
                      }
                    }
                    """.formatted(identityOf(component), identityOf(component))
            );
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
            assertJsonMatches(result, """
                    {
                      "circularRefCollection": [
                        {
                          "_id": "%s",
                          "_type": "CircularComponent",
                          "name": "Component1",
                          "dependency": {
                            "_id": "%s",
                            "_type": "CircularComponent",
                            "name": "Component2",
                            "dependency": {
                              "$ref": "%s",
                              "_type": "CircularComponent"
                            }
                          }
                        },
                        {
                          "$ref": "%s",
                          "_type": "CircularComponent"
                        }
                      ]
                    }
                    """.formatted(identityOf(component1), identityOf(component2),
                                  identityOf(component1), identityOf(component2))
            );
        }


        private static class CircularComponent implements DescribableComponent {

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

    private String normalizeJson(String json) {
        return json.replaceAll("\\s+", "");
    }

    private void assertJsonMatchesPattern(String actual, String expectedPattern) {
        String normalizedActual = normalizeJson(actual)
                .replaceAll("\"_id\":\"\\d+\"", "\"_id\":\"*\"");

        String normalizedExpected = normalizeJson(expectedPattern);

        assertEquals(normalizedExpected, normalizedActual,
                     "JSON does not match expected pattern");
    }

    private void assertJsonMatches(String actual, String expectedPattern) {
        String normalizedActual = normalizeJson(actual);
        String normalizedExpected = normalizeJson(expectedPattern);
        assertEquals(normalizedExpected, normalizedActual,
                     "JSON does not match expected");
    }

    private static int identityOf(Object component) {
        return System.identityHashCode(component);
    }

    private record SimpleTestComponent(String name, int value) implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("value", value);
        }
    }
}