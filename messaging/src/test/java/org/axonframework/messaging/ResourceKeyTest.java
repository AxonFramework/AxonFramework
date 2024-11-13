/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.messaging;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.messaging.Context.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ResourceKey}.
 *
 * @author Steven van Beelen
 */
class ResourceKeyTest {

    private static final String IDENTITY = "identity-name";
    private static final String DEBUG = "debug-text";

    @Test
    void resourceKeyCreateNoArgUsesValidUUIDAsToString() {
        ResourceKey<Object> resourceKey = ResourceKey.create();

        assertEquals(UUID.fromString(resourceKey.toString()).toString(), resourceKey.toString());
    }

    @Test
    void resourceKeyCreateNoArgMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.create();

        assertNotEquals(testSubject, ResourceKey.create());
    }

    @Test
    void resourceKeyCreateNoArgWithDebugStringMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.create().withDebugString(DEBUG);

        assertNotEquals(testSubject, ResourceKey.create().withDebugString(DEBUG));
    }

    @Test
    void resourceKeyCreateForClassShowsClassNameInToStringOutput() {
        ResourceKey<Integer> testSubject = ResourceKey.create(Integer.class);

        assertTrue(testSubject.toString().contains("Integer"));
    }

    @Test
    void resourceKeyCreateForClassMakesIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.create(Integer.class);

        assertEquals(testSubject, ResourceKey.create(Integer.class));
    }

    @Test
    void resourceKeyCreateForClassWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.create(Integer.class).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.create(Integer.class).withDebugString(DEBUG));
    }

    @Test
    void resourceKeyCreateForClassUsesClassNameHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(Integer.class.getName()), ResourceKey.create(Integer.class).hashCode());
    }

    @Test
    void resourceKeyCreateForIdentityStringShowsIdentityInToStringOutput() {
        ResourceKey<Object> testSubject = ResourceKey.create(IDENTITY);

        assertEquals(IDENTITY, testSubject.toString());
    }

    @Test
    void resourceKeyCreateForIdentityStringMakesIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.create(IDENTITY);

        assertEquals(testSubject, ResourceKey.create(IDENTITY));
    }

    @Test
    void resourceKeyCreateForIdentityStringWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.create(IDENTITY).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.create(IDENTITY).withDebugString(DEBUG));
    }

    @Test
    void resourceKeyCreateForIdentityUsesIdentityHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(IDENTITY), ResourceKey.create(IDENTITY).hashCode());
    }

    @Test
    void resourceKeyWithDebugStringShowsDebugStringOnToStringOutput() {
        // Test no-arg create.
        ResourceKey<Object> noArgTestSubject = ResourceKey.create().withDebugString(DEBUG);
        assertEquals(DEBUG, noArgTestSubject.toString());
        // Test given Class create.
        ResourceKey<Integer> clazzTestSubject = ResourceKey.create(Integer.class).withDebugString(DEBUG);
        assertEquals(DEBUG, clazzTestSubject.toString());
        // Test given identity String create.
        ResourceKey<Object> identityTestSubject = ResourceKey.create(IDENTITY).withDebugString(DEBUG);
        assertEquals(DEBUG, identityTestSubject.toString());
    }

    @Test
    void resourceKeyWithNullOrEmptyDebugStringThrowsAxonConfigurationException() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.create().withDebugString(null));
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.create().withDebugString(""));
    }
}
