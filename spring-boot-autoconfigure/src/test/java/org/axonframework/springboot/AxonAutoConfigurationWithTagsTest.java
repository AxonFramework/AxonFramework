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

package org.axonframework.springboot;

import org.axonframework.config.TagsConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerActuatorAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether gathering of Axon tags is properly configured.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class,
        AxonServerBusAutoConfiguration.class,
        AxonServerAutoConfiguration.class,
        AxonServerActuatorAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:test.tags.application.properties")
class AxonAutoConfigurationWithTagsTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void config() {
        TagsConfiguration tagsConfiguration = applicationContext.getBean(TagsConfiguration.class);
        assertNotNull(tagsConfiguration);
        Map<String, String> tags = tagsConfiguration.getTags();
        assertEquals(3, tags.size());
        assertEquals("Eu", tags.get("region"));
        assertEquals("It", tags.get("country"));
        assertEquals("Rome", tags.get("city"));
    }
}
