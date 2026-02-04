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

package org.axonframework.update.detection;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@link AxonVersionDetector} as best as possible. However, detection from JARs has had to be tested manually,
 * as there is no jar dependency available in the test resources to test against.
 * This test might be expanded if the Update Checker is moved out to a separate module, allowing for
 * a dependency on axon-messaging to be detected.
 */
class AxonVersionDetectorTest {

    /**
     * Tests that the version detector can parse the pom.properties in the test resources under
     * META-INF/maven/org.axonframework/axon-modelling/pom.properties.
     */
    @Test
    void detectsFilePomProperties() {
        var versions = AxonVersionDetector.safeDetectAxonModules();
        assertFalse(versions.isEmpty(), "Expected at least one Axon module version to be detected");
        assertTrue(versions.stream().anyMatch(v -> v.groupId().equals("org.axonframework") &&
                           v.artifactId().equals("axon-modelling") &&
                           v.version().equals("2.1-SNAPSHOT")),
                   "Expected Axon Modelling module to be detected");
    }
}