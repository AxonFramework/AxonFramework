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

package org.axonframework.springboot.integration;

import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Collections;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test ensures that any handler interceptor registered via {@link EventProcessingModule} is triggered.
 *
 * @author Milan Savic
 */
class EventProcessingModuleWithInterceptorsTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false")
                                                               .withUserConfiguration(TestContext.class);
    }

    private static <P> EventMessage<P> asEventMessage(P event) {
        return new GenericEventMessage<>(
                new GenericMessage<>(QualifiedNameUtils.fromClassName(event.getClass()), (P) event),
                () -> GenericEventMessage.clock.instant()
        );
    }

    @Test
    void interceptorRegistration() {
        testApplicationContext.run(context -> {
            EventBus eventBus = context.getBean(EventBus.class);
            TestContext.MyEventHandler myEventHandler = context.getBean(TestContext.MyEventHandler.class);

            eventBus.publish(asEventMessage("myEvent"));

            assertEquals("myMetaDataValue", myEventHandler.getMetaDataValue());
        });
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            EventProcessingModule eventProcessingModule = new EventProcessingModule();
            eventProcessingModule.usingSubscribingEventProcessors();
            eventProcessingModule.registerDefaultHandlerInterceptor((a, b) -> new MyInterceptor());
            return eventProcessingModule;
        }

        static class MyInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

            @Override
            public Object handle(@Nonnull UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 @Nonnull InterceptorChain interceptorChain)
                    throws Exception {
                unitOfWork.transformMessage(event -> event
                        .andMetaData(Collections.singletonMap("myMetaDataKey", "myMetaDataValue")));
                return interceptorChain.proceedSync();
            }
        }

        @Component
        static class MyEventHandler {

            private String metaDataValue;

            public String getMetaDataValue() {
                return metaDataValue;
            }

            @EventHandler
            public void on(String event, @MetaDataValue("myMetaDataKey") String metaDataValue) {
                this.metaDataValue = metaDataValue;
            }
        }
    }
}
