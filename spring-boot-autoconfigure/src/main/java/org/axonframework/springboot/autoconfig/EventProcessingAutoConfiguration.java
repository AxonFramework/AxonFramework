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

package org.axonframework.springboot.autoconfig;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.EventProcessingModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration for {@link EventProcessingModule}.
 *
 * @author Milan Savic
 * @since 4.0
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.axonframework.springboot.autoconfig.JpaAutoConfiguration",
        "org.axonframework.springboot.autoconfig.JdbcAutoConfiguration",
        "org.axonframework.springboot.autoconfig.JpaEventStoreAutoConfiguration",
        "org.axonframework.springboot.autoconfig.ObjectMapperAutoConfiguration",
        "org.axonframework.springboot.autoconfig.CBORMapperAutoConfiguration",
})
public class EventProcessingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean({EventProcessingModule.class, EventProcessingConfiguration.class})
    public EventProcessingModule eventProcessingModule() {
        return new EventProcessingModule();
    }
}
