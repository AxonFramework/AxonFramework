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

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration(classes = AnnotationDrivenConfigurationTest_CustomValues.Context.class)
public class AnnotationDrivenConfigurationTest_CustomValues {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    @Test
    void commandHandlerPostProcessorBeanDefinitionContainCustomValues() {
        BeanDefinition beanDefinition =  beanFactory.getBeanDefinition(
                "__axon-annotation-command-handler-bean-post-processor");
        assertEquals(AnnotationCommandHandlerBeanPostProcessor.class.getName(), beanDefinition.getBeanClassName());
    }

    @AnnotationDriven
    @Configuration
    public static class Context {

        @Bean
        public AnnotatedEventHandler eventHandler() {
            return new AnnotatedEventHandler();
        }

        @Bean
        public AnnotatedCommandHandler commandHandler() {
            return new AnnotatedCommandHandler();
        }

    }

    public static class AnnotatedEventHandler {

        @EventHandler
        public void on(String someEvent) {
        }
    }

    public static class AnnotatedCommandHandler {

        @CommandHandler
        public void on(String someEvent) {
        }
    }
}
