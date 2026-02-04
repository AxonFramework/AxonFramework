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
 * As Axon Framework does not depend on Kotlin, we cannot test the Kotlin version detection in a meaningful way.
 * This class is a placeholder for future meaningful tests when the update checker is moved.
 */
class KotlinVersionTest {

    @Test
    void doesNotDetectKotlinVersion() {
        // This test is a placeholder. The Kotlin version detection is not tested here as Axon Framework does not depend on Kotlin.
        // When the update checker is moved to a module that depends on Kotlin, this test can be updated to check the Kotlin version.
        assertEquals("none", KotlinVersion.get());
    }

}