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

package org.axonframework.messaging;

import org.axonframework.messaging.Context.ResourceKey;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ResourceKey}.
 *
 * @author Steven van Beelen
 */
class ResourceKeyTest {

    private static final String TEST_LABEL = "testLabel";

    @Test
    void resourceKeysShowDebugStringInOutput() {
        ResourceKey<Object> resourceKey = ResourceKey.withLabel(TEST_LABEL);

        assertTrue(resourceKey.toString().contains(TEST_LABEL));
    }

    @Test
    void resourceKeysWithEmptyDebugKeyShowsKeyIdOnly() {
        ResourceKey<Object> resourceKey = ResourceKey.withLabel("");

        assertFalse(resourceKey.toString().contains("["));
    }

    @Test
    void resourceKeysWithNullDebugKeyShowsKeyIdOnly() {
        ResourceKey<Object> resourceKey = ResourceKey.withLabel(null);

        assertFalse(resourceKey.toString().contains("["));
    }

    @Test
    void resourceKeysWithIdenticalLabelsAreNotEqual() {
        assertNotEquals(ResourceKey.withLabel(TEST_LABEL), ResourceKey.withLabel(TEST_LABEL));
    }
}
