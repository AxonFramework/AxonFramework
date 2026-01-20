/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.examples.university;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

class UniversityTestApplicationITest {

  @Nested
  @SpringBootTest(classes = UniversityExampleApplication.class)
  @ActiveProfiles("itest")
  @Testcontainers
  class DefaultTests {

    @ServiceConnection
    @Container
    private static final AxonServerContainer axonServer = new AxonServerContainer()
      .withAxonServerHostname("localhost")
      .withReuse(true)
      .withDevMode(true);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
      registry.add("axon.axonserver.servers", axonServer::getAxonServerAddress);
    }

    @BeforeAll
    static void setContextUp() throws Exception {
      axonServer.start();

      // Mainly needed to create DBC context now:
      AxonServerContainerUtils.purgeEventsFromAxonServer(axonServer.getHost(),
        axonServer.getHttpPort(),
        "default",
        AxonServerContainerUtils.DCB_CONTEXT);
    }

    @Test
    void applicationStarts() {
      // just run
    }
  }
}
