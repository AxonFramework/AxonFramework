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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemComponentDescriptorTest {

    private FilesystemComponentDescriptor testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new FilesystemComponentDescriptor();
    }

    @Test
    void describeBasicProperties() {
        // given
        testSubject.describeProperty("stringValue", "test");
        testSubject.describeProperty("numberValue", 42L);
        testSubject.describeProperty("booleanValue", true);

        // when
        var result = testSubject.describe();

        // then
        var expected = """
                /
                ├── stringValue: test
                ├── numberValue: 42
                └── booleanValue: true
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    @Test
    void describeSimpleComponent() {
        // given
        var component = new SimpleComponent("TestComponent", 100);
        testSubject.describeProperty("component", component);

        // when
        var result = testSubject.describe();

        // then
        var expected = """
                /
                └── component/
                    ├── _type: SimpleComponent
                    ├── name: TestComponent
                    └── value: 100
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    @Test
    void describeCollectionOfComponents() {
        // given
        var components = List.of(
                new SimpleComponent("Component1", 101),
                new SimpleComponent("Component2", 102)
        );
        testSubject.describeProperty("components", components);

        // when
        var result = testSubject.describe();

        // then
        var expected = """
                /
                └── components/
                    ├── [0]/
                    │   ├── _type: SimpleComponent
                    │   ├── name: Component1
                    │   └── value: 101
                    └── [1]/
                        ├── _type: SimpleComponent
                        ├── name: Component2
                        └── value: 102
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    @Test
    void describeMapOfComponents() {
        // given
        var map = Map.of(
                "firstComponent", new SimpleComponent("Component1", 201),
                "secondComponent", new SimpleComponent("Component2", 202)
        );
        testSubject.describeProperty("componentMap", map);

        // when
        String result = testSubject.describe();

        // then
        // Map order is not guaranteed, so we check for the presence of key elements
        // Example result looks like:
        // /
        //└── componentMap/
        //    ├── firstComponent/
        //    │   ├── _type: SimpleComponent
        //    │   ├── name: Component1
        //    │   └── value: 201
        //    └── secondComponent/
        //        ├── _type: SimpleComponent
        //        ├── name: Component2
        //        └── value: 202
        assertTrue(result.contains("componentMap/"));
        assertTrue(result.contains("firstComponent/"));
        assertTrue(result.contains("secondComponent/"));
        assertTrue(result.contains("_type: SimpleComponent"));
        assertTrue(result.contains("name: Component1"));
        assertTrue(result.contains("name: Component2"));
        assertTrue(result.contains("value: 201"));
        assertTrue(result.contains("value: 202"));
    }

    @Test
    void describeCircularReferences() {
        // given
        var component1 = new CircularComponent("Component1");
        var component2 = new CircularComponent("Component2");
        component1.setReference(component2);
        component2.setReference(component1);
        testSubject.describeProperty("circularRef", component1);

        // when
        var result = testSubject.describe();

        // then
        var expected = """
                /
                └── circularRef/
                    ├── _type: CircularComponent
                    ├── name: Component1
                    └── reference/
                        ├── _type: CircularComponent
                        ├── name: Component2
                        └── reference -> /circularRef
                
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    @Test
    void describeNestedComponentWithCircularReferences() {
        // given
        var component1 = new CircularComponent("Component1");
        var component2 = new CircularComponent("Component2");
        component1.setReference(component2);
        component2.setReference(component1);

        var container = new ContainerComponent("Container", component1,
                                               List.of(component2, component1),
                                               Map.of("key1", component1, "key2", component2));

        testSubject.describeProperty("container", container);

        // when
        var result = testSubject.describe();

        // then
        var expected = """
                /
                └── container/
                    ├── _type: ContainerComponent
                    ├── name: Container
                    ├── mainComponent/
                    │   ├── _type: CircularComponent
                    │   ├── name: Component1
                    │   └── reference/
                    │       ├── _type: CircularComponent
                    │       ├── name: Component2
                    │       └── reference -> /container/mainComponent
                    ├── componentList/
                    │   ├── [0] -> /container/mainComponent/reference
                    │   └── [1] -> /container/mainComponent
                    └── componentMap/
                        ├── key1 -> /container/mainComponent
                        └── key2 -> /container/mainComponent/reference
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    @Test
    void describeSelfReferentialComponent() {
        // given
        var component = new CircularComponent("SelfRef");
        component.setReference(component);
        testSubject.describeProperty("selfRef", component);

        // when
        var result = testSubject.describe();
        System.out.println(result);  // Print for visual inspection during test development

        // then
        var expected = """
                /
                └── selfRef/
                    ├── _type: CircularComponent
                    ├── name: SelfRef
                    └── reference -> /selfRef
                """;
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    private String normalizeLineEndings(String input) {
        return input.replaceAll("\\r\\n", "\n").trim();
    }

    private record SimpleComponent(String name, int value) implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("value", value);
        }
    }

    private static class CircularComponent implements DescribableComponent {

        private final String name;
        private CircularComponent reference;

        CircularComponent(String name) {
            this.name = name;
        }

        void setReference(CircularComponent reference) {
            this.reference = reference;
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            if (reference != null) {
                descriptor.describeProperty("reference", reference);
            }
        }
    }

    private record ContainerComponent(String name, CircularComponent mainComponent,
                                      List<CircularComponent> componentList,
                                      Map<String, CircularComponent> componentMap) implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("mainComponent", mainComponent);
            descriptor.describeProperty("componentList", componentList);
            descriptor.describeProperty("componentMap", componentMap);
        }
    }
}