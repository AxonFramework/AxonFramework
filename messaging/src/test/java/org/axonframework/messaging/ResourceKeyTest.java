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
    void createNewUsesValidUUIDAsToString() {
        ResourceKey<Object> resourceKey = ResourceKey.createNew();

        assertEquals(UUID.fromString(resourceKey.toString()).toString(), resourceKey.toString());
    }

    @Test
    void createNewMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.createNew();

        assertNotEquals(testSubject, ResourceKey.createNew());
    }

    @Test
    void createNewWithDebugStringMakesUniqueResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.createNew().withDebugString(DEBUG);

        assertNotEquals(testSubject, ResourceKey.createNew().withDebugString(DEBUG));
    }

    @Test
    void getForClassShowsClassNameInToStringOutput() {
        ResourceKey<Integer> testSubject = ResourceKey.getFor(Integer.class);

        assertTrue(testSubject.toString().contains("Integer"));
    }

    @Test
    void getForClassMakesIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.getFor(Integer.class);

        assertEquals(testSubject, ResourceKey.getFor(Integer.class));
    }

    @Test
    void getForClassWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Integer> testSubject = ResourceKey.getFor(Integer.class).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.getFor(Integer.class).withDebugString(DEBUG));
    }

    @Test
    void getForClassUsesClassNameHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(Integer.class.getName()), ResourceKey.getFor(Integer.class).hashCode());
    }

    @Test
    void getForIdentityStringShowsIdentityInToStringOutput() {
        ResourceKey<Object> testSubject = ResourceKey.getFor(IDENTITY);

        assertEquals(IDENTITY, testSubject.toString());
    }

    @Test
    void getForIdentityStringMakesIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.getFor(IDENTITY);

        assertEquals(testSubject, ResourceKey.getFor(IDENTITY));
    }

    @Test
    void getForIdentityStringWithDebugStringIdenticalResourceKeys() {
        ResourceKey<Object> testSubject = ResourceKey.getFor(IDENTITY).withDebugString(DEBUG);

        assertEquals(testSubject, ResourceKey.getFor(IDENTITY).withDebugString(DEBUG));
    }

    @Test
    void getForIdentityUsesIdentityHashValueAsHashCodeOutput() {
        assertEquals(Objects.hash(IDENTITY), ResourceKey.getFor(IDENTITY).hashCode());
    }

    @Test
    void resourceKeyWithDebugStringShowsDebugStringOnToStringOutput() {
        // Test no-arg create.
        ResourceKey<Object> noArgTestSubject = ResourceKey.createNew().withDebugString(DEBUG);
        assertEquals(DEBUG, noArgTestSubject.toString());
        // Test given Class create.
        ResourceKey<Integer> clazzTestSubject = ResourceKey.getFor(Integer.class).withDebugString(DEBUG);
        assertEquals(DEBUG, clazzTestSubject.toString());
        // Test given identity String create.
        ResourceKey<Object> identityTestSubject = ResourceKey.getFor(IDENTITY).withDebugString(DEBUG);
        assertEquals(DEBUG, identityTestSubject.toString());
    }

    @Test
    void resourceKeyWithNullOrEmptyDebugStringThrowsAxonConfigurationException() {
        //noinspection DataFlowIssue
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.createNew().withDebugString(null));
        assertThrows(AxonConfigurationException.class, () -> ResourceKey.createNew().withDebugString(""));
    }
}
