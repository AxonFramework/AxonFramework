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

package org.axonframework.usage;

import static org.junit.jupiter.api.Assertions.*;

import org.axonframework.usage.api.UsageResponse;
import org.axonframework.usage.api.UsageResponseVulnerabilitySeverity;
import org.junit.jupiter.api.Test;

class UsageResponseTest {

    @Test
    void parsesTheResponseCorrectly() {
        String body = """
                cd=12345
                upd=org.axonframework:axon-messaging:5.0.1:"https://github.com/AxonFramework/axon-framework/releases/tag/v5.0.1"
                upd=org.axonframework:axon-modelling:5.0.1:"https://github.com/AxonFramework/axon-framework/releases/tag/v5.0.1"
                upd=io.axoniq.console:console-framework-client-spring-boot-starter:2.1.0:"https://github.com/AxonIQ/console-framework-client/releases/tag/v2.1.0"
                vul=org.axonframework:axon-modelling:5.0.1:MEDIUM:"The EntityModel can be abused as a Denial of Service attack vector: https://axoniq.io/vulnerabilities/2023-01-01"
                vul=org.axonframework:axon-messaging:5.0.1:HIGH:"The Jackson version supplied by default has an NSA-backdoor built int. Please upgrade Jackson to version 1337.0\"""";
        UsageResponse response = UsageResponse.fromRequest(body);
        assertEquals(12345, response.checkInterval());
        assertEquals(3, response.upgrades().size());

        assertEquals("org.axonframework", response.upgrades().get(0).groupId());
        assertEquals("axon-messaging", response.upgrades().get(0).artifactId());
        assertEquals("5.0.1", response.upgrades().get(0).latestVersion());
        assertEquals("https://github.com/AxonFramework/axon-framework/releases/tag/v5.0.1", response.upgrades().get(0).releaseNotesUrl());
        assertEquals("org.axonframework", response.upgrades().get(1).groupId());
        assertEquals("axon-modelling", response.upgrades().get(1).artifactId());
        assertEquals("5.0.1", response.upgrades().get(1).latestVersion());
        assertEquals("https://github.com/AxonFramework/axon-framework/releases/tag/v5.0.1", response.upgrades().get(1).releaseNotesUrl());
        assertEquals("io.axoniq.console", response.upgrades().get(2).groupId());
        assertEquals("console-framework-client-spring-boot-starter", response.upgrades().get(2).artifactId());
        assertEquals("2.1.0", response.upgrades().get(2).latestVersion());
        assertEquals("https://github.com/AxonIQ/console-framework-client/releases/tag/v2.1.0", response.upgrades().get(2).releaseNotesUrl());

        assertEquals(2, response.vulnerabilities().size());
        assertEquals("org.axonframework", response.vulnerabilities().get(0).groupId());
        assertEquals("axon-modelling", response.vulnerabilities().get(0).artifactId());
        assertEquals("5.0.1", response.vulnerabilities().get(0).fixVersion());
        assertEquals(UsageResponseVulnerabilitySeverity.MEDIUM, response.vulnerabilities().get(0).severity());
        assertEquals("The EntityModel can be abused as a Denial of Service attack vector: https://axoniq.io/vulnerabilities/2023-01-01",
                response.vulnerabilities().get(0).description());

        assertEquals("org.axonframework", response.vulnerabilities().get(1).groupId());
        assertEquals("axon-messaging", response.vulnerabilities().get(1).artifactId());
        assertEquals("5.0.1", response.vulnerabilities().get(1).fixVersion());
        assertEquals(UsageResponseVulnerabilitySeverity.HIGH, response.vulnerabilities().get(1).severity());
        assertEquals("The Jackson version supplied by default has an NSA-backdoor built int. Please upgrade Jackson to version 1337.0",
                response.vulnerabilities().get(1).description());
    }
}