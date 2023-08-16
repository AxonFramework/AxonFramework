/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.spring.event.AxonStartedEvent;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;

import static org.mockito.Mockito.*;

class SpringAxonConfigurationTest {

    @Test
    void axonStartedEventIsPublished() {
        Configurer configurer = mock(Configurer.class);
        ApplicationContext context = mock(ApplicationContext.class);
        Configuration configuration = mock(Configuration.class);
        when(configurer.buildConfiguration()).thenReturn(configuration);

        SpringAxonConfiguration springAxonConfiguration = new SpringAxonConfiguration(configurer);
        springAxonConfiguration.setApplicationContext(context);

        springAxonConfiguration.start();
        verify(context).publishEvent(isA(AxonStartedEvent.class));
    }
}
