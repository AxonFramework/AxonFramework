/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.axonframework.springboot.util;

import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.axonframework.springboot.util.XStreamSecurityTypeUtility.autoConfigBasePackages;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link XStreamSecurityTypeUtility} through the {@link ApplicationContextRunner}. This
 * ensures an actual {@link org.springframework.context.ApplicationContext} is used for validating.
 *
 * @author Steven van Beelen
 */
class XStreamSecurityTypeUtilityTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false");
    }

    @Test
    void testAutoConfigBasePackages() {
        String[] expected = new String[]{
                "org.axonframework.springboot.util.**",
                "org.axonframework.eventhandling.tokenstore.**",
                "org.axonframework.modelling.saga.repository.jpa.**",
                "org.axonframework.eventsourcing.eventstore.jpa.**"
        };
        testApplicationContext.withUserConfiguration(TestContextWithSpringBootApplication.class)
                              .run(context -> {
                                  String[] result = autoConfigBasePackages(context.getSourceApplicationContext());
                                  assertArrayEquals(expected, result);
                              });
    }

    @Test
    void testAutoConfigBasePackagesWithCustomScanBasePackagesDoesNotChangeOutcome() {
        String[] expected = new String[]{
                "org.axonframework.springboot.util.**",
                "org.axonframework.eventhandling.tokenstore.**",
                "org.axonframework.modelling.saga.repository.jpa.**",
                "org.axonframework.eventsourcing.eventstore.jpa.**"
        };
        testApplicationContext.withUserConfiguration(TestContextWithSpringBootApplicationWithCustomBasePackages.class)
                              .run(context -> {
                                  String[] result = autoConfigBasePackages(context.getSourceApplicationContext());
                                  assertArrayEquals(expected, result);
                              });
    }

    @Test
    void testAutoConfigBasePackagesWithCustomScanBasePackageClassesDoesNotChangeOutcome() {
        String[] expected = new String[]{
                "org.axonframework.springboot.util.**",
                "org.axonframework.eventhandling.tokenstore.**",
                "org.axonframework.modelling.saga.repository.jpa.**",
                "org.axonframework.eventsourcing.eventstore.jpa.**"
        };
        testApplicationContext.withUserConfiguration(
                                      TestContextWithSpringBootApplicationWithCustomBasePackageClasses.class
                              )
                              .run(context -> {
                                  String[] result = autoConfigBasePackages(context.getSourceApplicationContext());
                                  assertArrayEquals(expected, result);
                              });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithSpringBootApplication {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @SpringBootApplication
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithSpringBootApplicationWithCustomBasePackages {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @SpringBootApplication(scanBasePackages = "org.axonframework.springboot.util")
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithSpringBootApplicationWithCustomBasePackageClasses {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @SpringBootApplication(scanBasePackageClasses = XStreamSecurityTypeUtilityTest.class)
        private static class MainClass {

        }
    }
}