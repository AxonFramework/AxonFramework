/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.boot;

import io.axoniq.axonhub.client.AxonHubConfiguration;
import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.query.subscription.EnhancedAxonHubQueryBus;
import io.axoniq.axonhub.client.query.QueryPriorityCalculator;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.serialization.Serializer;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by Sara Pellegrini on 16/05/2018.
 * sara.pellegrini@gmail.com
 */
@Configuration
@ConditionalOnClass(QueryUpdateEmitter.class)
@AutoConfigureBefore(MessagingAutoConfiguration.class)
public class SubscriptionQueryAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean(QueryBus.class)
    public EnhancedAxonHubQueryBus queryBus(PlatformConnectionManager platformConnectionManager, AxonHubConfiguration axonHubConfiguration,
                                    AxonConfiguration axonConfiguration, TransactionManager txManager,
                                    @Qualifier("messageSerializer") Serializer messageSerializer,
                                    Serializer genericSerializer,
                                    QueryPriorityCalculator priorityCalculator, QueryInvocationErrorHandler queryInvocationErrorHandler) {
        SimpleQueryBus simpleQueryBus = new SimpleQueryBus(axonConfiguration.messageMonitor(QueryBus.class, "queryBus"),
                                                     txManager, queryInvocationErrorHandler);
        return new EnhancedAxonHubQueryBus(platformConnectionManager, axonHubConfiguration,
                                           simpleQueryBus, simpleQueryBus,
                                           messageSerializer, genericSerializer, priorityCalculator);
    }

}
