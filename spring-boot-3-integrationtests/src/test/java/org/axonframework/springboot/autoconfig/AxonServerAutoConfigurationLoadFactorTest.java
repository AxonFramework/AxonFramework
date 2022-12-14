/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.springboot.autoconfig;

import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link CommandLoadFactorProvider} is correctly auto configured.
 *
 * @author Sara Pellegrini
 */
@ContextConfiguration
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.commandLoadFactor-autoConfiguration.properties")
public class AxonServerAutoConfigurationLoadFactorTest {

    @Autowired
    private CommandLoadFactorProvider commandLoadFactorProvider;

    @Test
    public void loadFactor() {
        int loadFactor = commandLoadFactorProvider.getFor("anything");
        assertEquals(36, loadFactor);
    }
}
