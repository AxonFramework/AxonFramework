/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.reactorlesstest;

import org.axonframework.queryhandling.QueryBus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests starting a Spring Boot application without Project Reactor on the classpath.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class ReactorlessStartupTest {

    @Autowired
    private QueryBus queryBus;

    @Test
    void contextLoadsWithQueryBus() {
        assertNotNull(queryBus);
    }
}
