/*
 * Copyright (c) 2010-2012. Axon Framework
 *
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

package org.axonframework.common.annotation;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(classes = {SpringBeanParameterResolverFactoryTest.AppContext.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringBeanParameterResolverFactoryTest {

    @Autowired
    private AnnotatedEventHandlerWithResources annotatedHandler;

    @Autowired
    private ApplicationContext applicationContext;

    private static final AtomicInteger counter = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        counter.set(0);
    }

    @Test
    public void testMethodsAreProperlyInjected() {
        assertNotNull(annotatedHandler);
        assertTrue(annotatedHandler instanceof EventListener);
        ((EventListener) annotatedHandler).handle(asEventMessage("Hello"));
        // make sure the invocation actually happened
        assertEquals(1, counter.get());
    }

    @Test(expected = BeanCreationException.class)
    public void testMethodsAreProperlyInjected_ErrorOnMissingParameterType() {
        // this should generate an error
        applicationContext.getBean("missingResourceHandler");
    }

    @Test(expected = BeanCreationException.class)
    public void testMethodsAreProperlyInjected_ErrorOnDuplicateParameterType() {
        // this should generate an error
        applicationContext.getBean("duplicateResourceHandler");
    }

    @Test
    public void testMethodsAreProperlyInjected_DuplicateParameterTypeWithPrimary() {
        // this should generate an error
        assertNotNull(applicationContext.getBean("duplicateResourceHandlerWithPrimary"));
    }

    @ImportResource(value = {"classpath:/contexts/spring-parameter-resolver.xml"})
    @Configuration
    public static class AppContext {

        @Bean(name = "annotatedHandler")
        public AnnotatedEventHandlerWithResources createHandler() {
            return new AnnotatedEventHandlerWithResources();
        }

        @Lazy
        @Bean
        public MissingResourceHandler missingResourceHandler() {
            return new MissingResourceHandler();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandler duplicateResourceHandler() {
            return new DuplicateResourceHandler();
        }

        @Lazy
        @Bean
        public DuplicateResourceHandlerWithPrimary duplicateResourceHandlerWithPrimary() {
            return new DuplicateResourceHandlerWithPrimary();
        }

        @Bean
        public DuplicateResource duplicateResource1() {
            return new DuplicateResource() {
            };
        }

        @Bean
        public DuplicateResource duplicateResource2() {
            return new DuplicateResource() {
            };
        }

        @Primary
        @Bean
        public DuplicateResourceWithPrimary duplicateResourceWithPrimary1() {
            return new DuplicateResourceWithPrimary() {
            };
        }

        @Bean
        public DuplicateResourceWithPrimary duplicateResourceWithPrimary2() {
            return new DuplicateResourceWithPrimary() {
            };
        }
    }

    public static interface DuplicateResource {

    }

    public static interface DuplicateResourceWithPrimary {

    }

    public static class MissingResourceHandler {

        @EventHandler
        public void handle(String message, DataSource dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandler {

        @EventHandler
        public void handle(String message, DuplicateResource dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class DuplicateResourceHandlerWithPrimary {

        @EventHandler
        public void handle(String message, DuplicateResourceWithPrimary dataSource) {
            counter.incrementAndGet();
        }
    }

    public static class AnnotatedEventHandlerWithResources {

        @EventHandler
        public void handle(String message, CommandBus commandBus, EventBus eventBus) {
            assertNotNull(message);
            assertNotNull(commandBus);
            assertNotNull(eventBus);
            counter.incrementAndGet();
        }
    }
}
