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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
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
    void testEmptyComponentScanAnnotatedBeanAllowsBeanPackageNameAsWildcard() {
        String expectedPackageWildcard = "org.axonframework.springboot.util.**";
        testApplicationContext.withUserConfiguration(TestContextWithEmptyComponentScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowTypesFromComponentScanAnnotatedBeans(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testBasePackageClassesComponentScanAnnotatedBeanAllowsTypes() {
        Class<String> expectedType = String.class;
        testApplicationContext.withUserConfiguration(TestContextWithBasePackageClassesComponentScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowTypesFromComponentScanAnnotatedBeans(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypes(aryEq(new Class[]{expectedType}));
                              });
    }

    @Test
    void testBasePackagesComponentScanAnnotatedBeanAllowsTypeByWildcard() {
        String expectedPackageWildcard = "foo.bar.**";
        testApplicationContext.withUserConfiguration(TestContextWithBasePackagesComponentScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowTypesFromComponentScanAnnotatedBeans(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testValuesComponentScanAnnotatedBeanAllowsTypeByWildcard() {
        String expectedPackageWildcard = "bar.baz.**";
        testApplicationContext.withUserConfiguration(TestContextWithValuesComponentScan.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowTypesFromComponentScanAnnotatedBeans(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @Test
    void testSpringBootApplicationAnnotatedBeanAllowsBeanPackageNameAsWildcard() {
        String expectedPackageWildcard = "org.axonframework.springboot.util.**";
        testApplicationContext.withUserConfiguration(TestContextWithSpringBootApplication.class)
                              .run(context -> {
                                  XStreamSecurityTypeUtility.allowTypesFromComponentScanAnnotatedBeans(
                                          context.getSourceApplicationContext(), testSubject
                                  );
                                  verify(testSubject).allowTypesByWildcard(aryEq(new String[]{expectedPackageWildcard}));
                              });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithEmptyComponentScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @ComponentScan
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithBasePackageClassesComponentScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @ComponentScan(basePackageClasses = String.class)
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithBasePackagesComponentScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @ComponentScan(basePackages = "foo.bar")
        private static class MainClass {

        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class TestContextWithValuesComponentScan {

        @Bean
        private MainClass mainClass() {
            return new MainClass();
        }

        @ComponentScan(value = "bar.baz")
        private static class MainClass {

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