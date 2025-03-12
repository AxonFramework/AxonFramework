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

import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.util.Map;

/**
 * Test class validating the {@link StringBuilderComponentDescriptor}.
 *
 * @author Steven van Beelen
 */
class StringBuilderComponentDescriptorTest {

    @Test
    void describeObjectInvokesToString() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        System.out.println(testSubject.describe());
    }

    @Test
    void describeObjectAsDescribableComponentDelegates() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        System.out.println(testSubject.describe());
    }

    @Test
    void describeCollection() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        System.out.println(testSubject.describe());
    }

    @Test
    void describeMap() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        Map<String, ? extends Serializable> testMap = Map.of("string", "value", "long", 42L, "boolean", true);

        System.out.println(testSubject.describe());
    }

    @Test
    void describeString() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        testSubject.describeProperty("string", "string");

        System.out.println(testSubject.describe());
    }

    @Test
    void describeLong() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        testSubject.describeProperty("long", 42L);

        System.out.println(testSubject.describe());
    }

    @Test
    void describeBoolean() {
        ComponentDescriptor testSubject = new StringBuilderComponentDescriptor();

        testSubject.describeProperty("boolean", true);

        System.out.println(testSubject.describe());
    }
}