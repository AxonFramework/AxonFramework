package org.axonframework.update;

import org.axonframework.update.api.Artifact;
import org.axonframework.update.api.UpdateCheckRequest;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckRequestTest {

    @Test
    void toQueryString() {
        UpdateCheckRequest request = new UpdateCheckRequest(
                "machine-1234",
                "instance-5678",
                "Linux",
                "6.11.0-26-generic",
                "amd64",
                "17.0.2",
                "AdoptOpenJDK",
                "1.8.22",
                Arrays.asList(
                        new Artifact("org.axonframework", "axon-core", "5.0.0"),
                        new Artifact("org.axonframework.something", "axon-something", "5.0.0"),
                        new Artifact("org.axonframework.extensions", "axon-ext-bland", "5.0.0"),
                        new Artifact("org.axonframework.extensions.kafka", "axon-ext-kafka", "5.0.0"),
                        new Artifact("io.axoniq", "top-level-axoniq", "5.0.0"),
                        new Artifact("io.axoniq.sub", "sub-level-axoniq", "5.0.0"),
                        new Artifact("org.example", "example-lib", "1.2.3")
                )
        );

        String queryString = request.toQueryString();

        // Verify that all parameters are present and properly encoded
        assertTrue(queryString.contains("os=Linux%3B+6.11.0-26-generic%3B+amd64"));
        assertTrue(queryString.contains("java=17.0.2%3B+AdoptOpenJDK"));
        assertTrue(queryString.contains("kotlin=1.8.22"));
        assertTrue(queryString.contains("lib-fw.axon-core=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-fw.something.axon-something=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-ext.axon-ext-bland=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-ext.kafka.axon-ext-kafka=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-iq.top-level-axoniq=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-iq.sub.sub-level-axoniq=5.0.0"), queryString);
        assertTrue(queryString.contains("lib-org.example.example-lib=1.2.3"));
    }

    @Test
    void toUserAgent() {
        UpdateCheckRequest request = new UpdateCheckRequest(
                "machine-1234",
                "instance-5678",
                "Linux",
                "6.11.0-26-generic",
                "amd64",
                "17.0.2",
                "AdoptOpenJDK",
                "1.8.22",
                Collections.singletonList(new Artifact("org.axonframework", "axon-messaging", "5.0.1"))
        );

        String userAgent = request.toUserAgent();
        assertEquals("AxonIQ UpdateChecker/5.0.1 (Java 17.0.2 AdoptOpenJDK; Linux; 6.11.0-26-generic; amd64)",
                     userAgent);
    }
}