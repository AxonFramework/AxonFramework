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
    void uniqueKeyUsesValidUUIDAsToString() {
        ResourceKey<Object> resourceKey = ResourceKey.uniqueKey();

        assertEquals(UUID.fromString(resourceKey.toString()).toString(), resourceKey.toString());
    }

    @Test
    void uniqueKeyMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.uniqueKey();

        assertNotEquals(testSubject, ResourceKey.uniqueKey());
    }

    @Test
    void uniqueKeyWithDebugStringMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.uniqueKey().withDebugString(DEBUG);

        assertNotEquals(testSubject, ResourceKey.uniqueKey().withDebugString(DEBUG));
    }

    @Test
    void sharedKeyByClassShowsClassNameInToStringOutput() {
        ResourceKey<Integer> testSubject = ResourceKey.sharedKey(Integer.class);

        assertTrue(testSubject.toString().contains("Integer"));
    }

    @Test
    void sharedKeyByClassMakesIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.sharedKey(Integer.class);

        assertEquals(testSubject, ResourceKey.sharedKey(Integer.class));
    }

    @Test
    void sharedKeyByClassWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.sharedKey(Integer.class).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.sharedKey(Integer.class).withDebugString(DEBUG));
    }

    @Test
    void sharedKeyByClassUsesClassNameHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(Integer.class.getName()), ResourceKey.sharedKey(Integer.class).hashCode());
    }

    @Test
    void sharedKeyByIdentityStringShowsIdentityInToStringOutput() {
        ResourceKey<Object> testSubject = ResourceKey.sharedKey(IDENTITY);

        assertEquals(IDENTITY, testSubject.toString());
    }

    @Test
    void sharedKeyByIdentityStringMakesIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.sharedKey(IDENTITY);

        assertEquals(testSubject, ResourceKey.sharedKey(IDENTITY));
    }

    @Test
    void sharedKeyByIdentityStringWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.sharedKey(IDENTITY).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.sharedKey(IDENTITY).withDebugString(DEBUG));
    }

    @Test
    void sharedKeyByIdentityUsesIdentityHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(IDENTITY), ResourceKey.sharedKey(IDENTITY).hashCode());
    }

    @Test
    void resourceKeyWithDebugStringShowsDebugStringOnToStringOutput() {
        // Test no-arg create.
        ResourceKey<Object> noArgTestSubject = ResourceKey.uniqueKey().withDebugString(DEBUG);
        assertEquals(DEBUG, noArgTestSubject.toString());
        // Test given Class create.
        ResourceKey<Integer> clazzTestSubject = ResourceKey.sharedKey(Integer.class).withDebugString(DEBUG);
        assertEquals(DEBUG, clazzTestSubject.toString());
        // Test given identity String create.
        ResourceKey<Object> identityTestSubject = ResourceKey.sharedKey(IDENTITY).withDebugString(DEBUG);
        assertEquals(DEBUG, identityTestSubject.toString());
    }

    @Test
    void resourceKeyWithNullOrEmptyDebugStringThrowsAxonConfigurationException() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.uniqueKey().withDebugString(null));
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.uniqueKey().withDebugString(""));
    }
}
