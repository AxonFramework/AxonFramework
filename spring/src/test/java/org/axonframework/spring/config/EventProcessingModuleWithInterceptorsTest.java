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

package org.axonframework.spring.config;

import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import javax.annotation.Nonnull;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test ensures that any handler interceptor registered via {@link EventProcessingModule} is triggered.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class EventProcessingModuleWithInterceptorsTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private Context.MyEventHandler myEventHandler;

    @Test
    void testInterceptorRegistration() {
        eventBus.publish(GenericEventMessage.asEventMessage("myEvent"));
        assertEquals("myMetaDataValue", myEventHandler.getMetaDataValue());
    }

    @Import(SpringAxonAutoConfigurer.ImportSelector.class)
    @Configuration
    public static class Context {

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            EventProcessingModule eventProcessingModule = new EventProcessingModule();
            eventProcessingModule.usingSubscribingEventProcessors();
            eventProcessingModule.registerDefaultHandlerInterceptor((a, b) -> new MyInterceptor());
            return eventProcessingModule;
        }

        public class MyInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

            @Override
            public Object handle(@Nonnull UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 @Nonnull InterceptorChain interceptorChain)
                    throws Exception {
                unitOfWork.transformMessage(event -> event
                        .andMetaData(Collections.singletonMap("myMetaDataKey", "myMetaDataValue")));
                return interceptorChain.proceed();
            }
        }

        @Component
        public class MyEventHandler {

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
