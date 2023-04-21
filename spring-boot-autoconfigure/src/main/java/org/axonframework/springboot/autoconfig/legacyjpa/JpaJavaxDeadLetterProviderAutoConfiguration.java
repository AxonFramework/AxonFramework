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

package org.axonframework.springboot.autoconfig.legacyjpa;

import javax.persistence.EntityManagerFactory;

import org.axonframework.common.legacyjpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.deadletter.legacyjpa.JpaSequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.EventProcessingAutoConfiguration;
import org.axonframework.springboot.autoconfig.JpaAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Auto configuration class for Axon's JPA specific sequenced dead letter queue.
 * </p>
 * Can be excluded using {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration} to replace it for a
 * custom configuration.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 * @deprecated in favor of using {@link org.axonframework.springboot.autoconfig.JpaDeadLetterProviderAutoConfiguration}
 * which uses jakarta.
 */
@Deprecated
@AutoConfiguration
@AutoConfigureAfter({JpaJavaxAutoConfiguration.class, EventProcessingAutoConfiguration.class})
@ConditionalOnBean(EntityManagerFactory.class)
public class JpaJavaxDeadLetterProviderAutoConfiguration {

    @Autowired
    void registerDeadLetterProvider(
            EventProcessingModule processingModule,
            EntityManagerProvider entityManagerProvider,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            Serializer genericSerializer,
            TransactionManager transactionManager
    ) {
        processingModule.registerDeadLetterProvider(
                ((configuration, processingGroup) ->
                        JpaSequencedDeadLetterQueue
                                .builder()
                                .processingGroup(processingGroup)
                                .entityManagerProvider(entityManagerProvider)
                                .transactionManager(transactionManager)
                                .eventSerializer(eventSerializer)
                                .genericSerializer(genericSerializer)
                                .build()
                ));
    }
}
