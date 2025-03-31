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

class FilesystemStyleComponentDescriptorTest extends ComponentDescriptorTestSuite {

    @Override
    ComponentDescriptor testSubject() {
        return new FilesystemStyleComponentDescriptor();
    }

    @Override
    void assertDescribeNullString(String result) {
        var expected = """
                /
                └── nullString: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeNullLong(String result) {
        var expected = """
                /
                └── nullLong: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeNullBoolean(String result) {
        var expected = """
                /
                └── nullBoolean: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeNullObject(String result) {
        var expected = """
                /
                └── nullObject: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeNullMap(String result) {
        var expected = """
                /
                └── nullMap: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeNullList(String result) {
        var expected = """
                /
                └── nullList: null
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Test
    void describeNestedComponentWithCircularReferences() {
        // given
        var component1 = new CircularReferencesTests.CircularComponent("Component1");
        var component2 = new CircularReferencesTests.CircularComponent("Component2");
        component1.setDependency(component2);
        component2.setDependency(component1);

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
                    ├── _ref: %s
                    ├── _type: org.axonframework.common.infra.FilesystemStyleComponentDescriptorTest$ContainerComponent
                    ├── name: Container
                    ├── mainComponent/
                    │   ├── _ref: %s
                    │   ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    │   ├── name: Component1
                    │   └── dependency/
                    │       ├── _ref: %s
                    │       ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    │       ├── name: Component2
                    │       └── dependency -> /container/mainComponent
                    ├── componentList/
                    │   ├── [0] -> /container/mainComponent/dependency
                    │   └── [1] -> /container/mainComponent
                    └── componentMap/
                        ├── key1 -> /container/mainComponent
                        └── key2 -> /container/mainComponent/dependency
                """.formatted(identityOf(container), identityOf(component1), identityOf(component2));
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    private record ContainerComponent(String name, CircularReferencesTests.CircularComponent mainComponent,
                                      List<CircularReferencesTests.CircularComponent> componentList,
                                      Map<String, CircularReferencesTests.CircularComponent> componentMap)
            implements DescribableComponent {

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeProperty("name", name);
            descriptor.describeProperty("mainComponent", mainComponent);
            descriptor.describeProperty("componentList", componentList);
            descriptor.describeProperty("componentMap", componentMap);
        }
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
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeBoolean(String result) {
        var expected = """
                /
                └── testName: true
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeLong(String result) {
        var expected = """
                /
                └── testName: 42
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeString(String result) {
        var expected = """
                /
                └── testName: testValue
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeCollectionOfDescribableComponentsShouldIncludeTypeAndId(String result,
                                                                               SimpleTestComponent component1,
                                                                               SimpleTestComponent component2) {
        var expected = """
                /
                └── components/
                    ├── [0]/
                    │   ├── _ref: %s
                    │   ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$SimpleTestComponent
                    │   ├── name: component1
                    │   └── value: 101
                    └── [1]/
                        ├── _ref: %s
                        ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$SimpleTestComponent
                        ├── name: component2
                        └── value: 102
                """.formatted(identityOf(component1), identityOf(component2));
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeEmptyCollection(String result) {
        var expected = """
                /
                └── emptyCollection/
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeCollection(String result) {
        var expected = """
                /
                └── collection/
                    ├── [0]: value1
                    ├── [1]: value2
                    └── [2]: value3
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeMapWithDescribableComponentsShouldIncludeTypeAndId(String result,
                                                                          SimpleTestComponent component1) {
        var expected = """
                /
                └── componentMap/
                    └── component1/
                        ├── _ref: %s
                        ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$SimpleTestComponent
                        ├── name: value1
                        └── value: 201
                """.formatted(identityOf(component1));
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeEmptyMap(String result) {
        var expected = """
                /
                └── emptyMap/
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeMap(String result) {
        var expected = """
                /
                └── map/
                    ├── key1: value1
                    └── key2: value2
                """;
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeDescribableComponentShouldIncludeTypeAndId(String result, SimpleTestComponent component) {
        var expected = """
                /
                └── component/
                    ├── _ref: %s
                    ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$SimpleTestComponent
                    ├── name: componentValue
                    └── value: 100
                """.formatted(identityOf(component));
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeCollectionWithCircularReferences(
            String result,
            CircularReferencesTests.CircularComponent component1,
            CircularReferencesTests.CircularComponent component2
    ) {
        var expected = """
                /
                └── circularRefCollection/
                    ├── [0]/
                    │   ├── _ref: %s
                    │   ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    │   ├── name: Component1
                    │   └── dependency/
                    │       ├── _ref: %s
                    │       ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    │       ├── name: Component2
                    │       └── dependency -> /circularRefCollection[0]
                    └── [1] -> /circularRefCollection[0]/dependency
                """.formatted(identityOf(component1), identityOf(component2));
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeComponentWithSelfReference(String result, CircularReferencesTests.CircularComponent component) {
        var expected = """
                /
                └── selfRef/
                    ├── _ref: %s
                    ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    ├── name: SelfReferencing
                    └── dependency -> /selfRef
                """.formatted(identityOf(component));
        assertFilesystemStyleOutput(expected, result);
    }

    @Override
    void assertDescribeComponentWithCircularReference(
            String result,
            CircularReferencesTests.CircularComponent component1,
            CircularReferencesTests.CircularComponent component2
    ) {
        var expected = """
                /
                └── circularRef/
                    ├── _ref: %s
                    ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                    ├── name: Component1
                    └── dependency/
                        ├── _ref: %s
                        ├── _type: org.axonframework.common.infra.ComponentDescriptorTestSuite$CircularReferencesTests$CircularComponent
                        ├── name: Component2
                        └── dependency -> /circularRef
                """.formatted(identityOf(component1), identityOf(component2));
        assertFilesystemStyleOutput(expected, result);
    }

    void assertFilesystemStyleOutput(String expected, String result) {
        assertEquals(normalizeLineEndings(expected), normalizeLineEndings(result));
    }

    private String normalizeLineEndings(String input) {
        return input.replaceAll("\\r\\n", "\n").trim();
    }
}