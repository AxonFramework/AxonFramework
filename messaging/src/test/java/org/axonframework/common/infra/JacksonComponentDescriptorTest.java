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

    @Test
    void describeString() {
        // given
        testSubject.describeProperty("testName", "testValue");

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "testName" : "testValue"
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeLong() {
        // given
        testSubject.describeProperty("testName", 42L);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "testName" : 42
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeBoolean() {
        // given
        testSubject.describeProperty("testName", true);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "testName" : true
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeDescribableComponent() {
        // given
        var component = new SimpleTestComponent("componentValue", 100);
        testSubject.describeProperty("component", component);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "component" : {
                                                 "name" : "componentValue",
                                                 "value" : 100
                                               }
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeCollection() {
        // given
        var collection = List.of("value1", "value2", "value3");
        testSubject.describeProperty("collection", collection);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "collection" : [ "value1", "value2", "value3" ]
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeEmptyCollection() {
        // given
        var emptyCollection = List.<String>of();
        testSubject.describeProperty("emptyCollection", emptyCollection);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "emptyCollection" : [ ]
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

    @Test
    void describeCollectionOfDescribableComponents() {
        // given
        var components = List.of(
                new SimpleTestComponent("component1", 101),
                new SimpleTestComponent("component2", 102)
        );
        testSubject.describeProperty("components", components);

        // when
        var result = testSubject.describe();
        var expected = normalizeJson("""
                                             {
                                               "components" : [ {
                                                 "name" : "component1",
                                                 "value" : 101
                                               }, {
                                                 "name" : "component2",
                                                 "value" : 102
                                               } ]
                                             }
                                             """);

        // then
        assertEquals(expected, normalizeJson(result));
    }

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
    void describeMapWithDescribableComponents() {
        // given
        var map = Map.of(
                "component1", new SimpleTestComponent("value1", 201),
                "component2", new SimpleTestComponent("value2", 202)
        );
        testSubject.describeProperty("componentMap", map);

        // when
        var result = testSubject.describe();

        // then
        // since maps don't guarantee order in JSON representation, we need to check both possibilities
        var expected1 = normalizeJson("""
                                              {
                                                "componentMap" : {
                                                  "component1" : {
                                                    "name" : "value1",
                                                    "value" : 201
                                                  },
                                                  "component2" : {
                                                    "name" : "value2",
                                                    "value" : 202
                                                  }
                                                }
                                              }
                                              """);
        var expected2 = normalizeJson("""
                                              {
                                                "componentMap" : {
                                                  "component2" : {
                                                    "name" : "value2",
                                                    "value" : 202
                                                  },
                                                  "component1" : {
                                                    "name" : "value1",
                                                    "value" : 201
                                                  }
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
    void describeComplexStructure() {
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
        var expected = normalizeJson("""
                                             {
                                               "complexStructure" : {
                                                 "stringValue" : "test",
                                                 "numberValue" : 42,
                                                 "booleanValue" : true,
                                                 "component" : {
                                                   "name" : "nestedComponent",
                                                   "value" : 300
                                                 },
                                                 "simpleList" : [ "listItem1", "listItem2" ],
                                                 "componentList" : [ {
                                                   "name" : "listComponent1",
                                                   "value" : 301
                                                 }, {
                                                   "name" : "listComponent2",
                                                   "value" : 302
                                                 } ],
                                                 "simpleMap" : {
                                                   "mapKey1" : "mapValue1"
                                                 }
                                               }
                                             }
                                             """);

        assertEquals(expected, normalizeJson(result));
    }

    private String normalizeJson(String json) {
        return json.replaceAll("\\s+", "");
    }

    private record SimpleTestComponent(String name, int value) implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("value", value);
        }
    }
}