/*
 * Copyright (c) 2010-2017. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.boot.autoconfig;

import org.axonframework.boot.DistributedCommandBusProperties;
import org.axonframework.commandhandling.distributed.CommandBusConnector;
import org.axonframework.commandhandling.distributed.CommandRouter;
import org.axonframework.commandhandling.distributed.DistributedCommandBus;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@AutoConfigureAfter({SpringCloudAutoConfiguration.class, JGroupsAutoConfiguration.class})
@ConditionalOnBean({CommandRouter.class})
public class DistributedAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(CommandBusConnector.class)
    @ConditionalOnMissingBean
    public DistributedCommandBus distributedCommandBus(CommandRouter router,
                                                       CommandBusConnector connector,
                                                       DistributedCommandBusProperties distributedCommandBusProperties) {
        DistributedCommandBus commandBus = new DistributedCommandBus(router, connector);
        commandBus.updateLoadFactor(distributedCommandBusProperties.getLoadFactor());
        return commandBus;
    }

}
