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

package org.axonframework.updates.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckRequestTest {

    @Test
    void testToQueryString() {
        UpdateCheckRequest request = new UpdateCheckRequest(
                "machine-1234",
                "instance-5678",
                "Linux",
                "6.11.0-26-generic",
                "amd64",
                "17.0.2",
                "AdoptOpenJDK",
                "1.8.22",
                List.of(
                        new Artifact("org.axonframework", "axon-core", "5.0.0"),
                        new Artifact("org.example", "example-lib", "1.2.3")
                )
        );

        String queryString = request.toQueryString();

        // Verify that all parameters are present and properly encoded
        assertTrue(queryString.contains("os=Linux%3B+6.11.0-26-generic%3B+amd64"));
        assertTrue(queryString.contains("java=17.0.2%3B+AdoptOpenJDK"));
        assertTrue(queryString.contains("kotlin=1.8.22"));
        assertTrue(queryString.contains("lib=org.axonframework%3Aaxon-core%3A5.0.0"));
        assertTrue(queryString.contains("lib=org.example%3Aexample-lib%3A1.2.3"));
    }

    @Test
    void testToUserAgent() {
        UpdateCheckRequest request = new UpdateCheckRequest(
                "machine-1234",
                "instance-5678",
                "Linux",
                "6.11.0-26-generic",
                "amd64",
                "17.0.2",
                "AdoptOpenJDK",
                "1.8.22",
                List.of(
                        new Artifact("org.axonframework", "axon-messaging", "5.0.1")
                )
        );

        String userAgent = request.toUserAgent();
        assertEquals("AxonIQ UpdateChecker/5.0.1 (Java 17.0.2 AdoptOpenJDK; Linux; 6.11.0-26-generic; amd64)", userAgent);
    }
}