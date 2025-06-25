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

package org.axonframework.usage.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsageRequestTest {

    @Test
    void testSerialize() {
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
        String expected = "mid=machine-1234\n" +
                "iid=instance-5678\n" +
                "osn=Linux\n" +
                "osv=6.11.0-26-generic\n" +
                "osa=amd64\n" +
                "jvr=17.0.2\n" +
                "jvn=AdoptOpenJDK\n" +
                "ktv=1.8.22\n" +
                "lib=org.axonframework:axon-core:5.0.0\n" +
                "lib=org.example:example-lib:1.2.3\n";
        assertEquals(expected, request.serialize());
    }
}