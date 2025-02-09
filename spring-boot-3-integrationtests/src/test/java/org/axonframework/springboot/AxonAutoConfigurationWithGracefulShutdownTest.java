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

package org.axonframework.springboot;

import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.shutdown=graceful",
        "management.endpoints.web.exposure.include=*",
        "management.endpoint.shutdown.enabled=true"})
@Testcontainers
public class AxonAutoConfigurationWithGracefulShutdownTest {

    @Container
    @ServiceConnection
    private final static AxonServerContainer axonServer = new AxonServerContainer().withDevMode(true);

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DirtiesContext
    void testShutdown() {
        ResponseEntity<Map<String, Object>> entity = asMapEntity(
                this.restTemplate.postForEntity("http://localhost:" + port + "/actuator/shutdown", null, Map.class));
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((String) entity.getBody().get("message"))).contains("Shutting down");
    }

    @Test
    void testSimpleEndpoint() {
        ResponseEntity<String> response = this.restTemplate.getForEntity("http://localhost:" + port + "/simple", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Simple message");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <K, V> ResponseEntity<Map<K, V>> asMapEntity(ResponseEntity<Map> entity) {
        return (ResponseEntity) entity;
    }

    @TestConfiguration
    public static class TestConfig {

        @Bean
        public TestRestTemplate testRestTemplate() {
            return new TestRestTemplate();
        }

        @RestController
        public static class SimpleController {

            @GetMapping("/simple")
            public ResponseEntity<String> getSimpleMessage() {
                return ResponseEntity.ok("Simple message");
            }
        }
    }
}
