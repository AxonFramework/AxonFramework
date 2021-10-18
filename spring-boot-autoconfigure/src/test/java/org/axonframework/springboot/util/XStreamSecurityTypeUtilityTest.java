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

import com.thoughtworks.xstream.XStream;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.mockito.AdditionalMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link XStreamSecurityTypeUtility} through the {@link ApplicationContextRunner}. This
 * ensures an actual {@link org.springframework.context.ApplicationContext} is used for validating.
 *
 * @author Steven van Beelen
 */
class XStreamSecurityTypeUtilityTest {

    private ApplicationContextRunner testApplicationContext;
    private XStream testSubject;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withPropertyValues("axon.axonserver.enabled:false");
        testSubject = spy(new XStream());
    }

    @Test
    void testEmptyEntityScanAnnotatedBeanAllowsBeanPackageNameAsWildcard() {
        String expectedPackageWildcard = "org.axonframework.springboot.util.**";
        testApplicationContext.withUserConfiguration(TestContextWithEmptyEntityScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testBasePackageClassesEntityScanAnnotatedBeanAllowsTypes() {
        Class<String> expectedType = String.class;
        testApplicationContext.withUserConfiguration(TestContextWithBasePackageClassesEntityScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypes(aryEq(new Class[]{expectedType}));
                              });
    }

    @Test
    void testBasePackagesEntityScanAnnotatedBeanAllowsTypeByWildcard() {
        String expectedPackageWildcard = "foo.bar.**";
        testApplicationContext.withUserConfiguration(TestContextWithBasePackagesEntityScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testBasePackagesAndClassesEntityScanAnnotatedBeanAllowsTypesAndAllowsTypesByWildcard() {
        Class<Integer> expectedType = Integer.class;
        String expectedPackageWildcard = "foo.bar.baz.**";
        testApplicationContext.withUserConfiguration(
                                      TestContextWithBasePackageClassesAndBasePackagesEntityScan.class
                              )
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypes(aryEq(new Class[]{expectedType}));
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testValuesEntityScanAnnotatedBeanAllowsTypeByWildcard() {
        String expectedPackageWildcard = "bar.baz.**";
        testApplicationContext.withUserConfiguration(TestContextWithValuesEntityScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testSpringBootApplicationAnnotatedBeanIsIgnoredInFavorOfEntityScanAnnotatedBean() {
        String expectedPackageWildcard = "foo.bar.**";
        testApplicationContext.withUserConfiguration(TestContextWithSpringBootApplicationAndEntityScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testSpringBootApplicationAnnotatedBeanAllowsBeanPackageNameAsWildcardAsDefault() {
        String[] expectedPackageWildcards = new String[]{
                "org.axonframework.springboot.util.**",
                "org.axonframework.eventhandling.tokenstore.**",
                "org.axonframework.modelling.saga.repository.jpa.**",
                "org.axonframework.eventsourcing.eventstore.jpa.**"
        };
        testApplicationContext.withUserConfiguration(TestContextWithSpringBootApplication.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowEntityTypesFrom(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(expectedPackageWildcards));
                              });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithEmptyEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @EntityScan
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithBasePackageClassesEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @EntityScan(basePackageClasses = String.class)
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithBasePackagesEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @EntityScan(basePackages = "foo.bar")
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithBasePackageClassesAndBasePackagesEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @EntityScan(basePackageClasses = Integer.class, basePackages = "foo.bar.baz")
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithValuesEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @EntityScan(value = "bar.baz")
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithSpringBootApplicationAndEntityScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @Bean
        private EntityConfiguration entityConfiguration() {
            return new EntityConfiguration();
        }

        @SpringBootApplication
        private static class MainClass {

        }

        @EntityScan(basePackages = "foo.bar")
        private static class EntityConfiguration {

        }
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
}