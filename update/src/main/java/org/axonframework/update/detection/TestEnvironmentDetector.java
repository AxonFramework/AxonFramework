/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.update.detection;

import org.axonframework.common.annotation.Internal;

import java.util.Arrays;

/**
 * This class detects whether the usage reporter is being used in a unit test environment, such as a JUnit test or
 * another testing framework.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class TestEnvironmentDetector {

    /**
     * System property to force the usage reporter to assume it's not a test environment. This is useful for testing
     * purposes, where you want to skip the detection logic.
     */
    public static final String AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT = "axoniq.usage.force-test-environment";

    /**
     * Checks whether the current environment is a test environment.
     *
     * @return {@code true} if the current environment is a test environment, {@code false} otherwise.
     */
    public static boolean isTestEnvironment() {
        if (System.getProperty(AXONIQ_USAGE_FORCE_TEST_ENVIRONMENT, "false").equals("true")) {
            return false; // Skip detection if explicitly configured
        }
        return Arrays.stream(Thread.currentThread().getStackTrace())
                     .anyMatch(TestEnvironmentDetector::isTestClass);
    }

    private static boolean isTestClass(StackTraceElement stackTraceElement) {
        String className = stackTraceElement.getClassName();
        return className.startsWith("org.junit") // JUnit 4, JUnit Vintage
                || className.startsWith("org.testng") // TestNG
                || className.startsWith("org.spockframework") // Spock
                || className.startsWith("org.mockito") // Mockito
                || className.startsWith("io.cucumber") // Cucumber (modern)
                || className.startsWith("org.cucumber") // Cucumber (legacy)
                || className.startsWith("org.assertj") // AssertJ
                || className.startsWith("org.hamcrest") // Hamcrest
                || className.startsWith("org.jboss.arquillian") // Arquillian
                || className.startsWith("org.arquillian") // Arquillian (alt)
                || className.startsWith("org.springframework.test") // Spring Test
                || className.startsWith("org.springframework.boot.test") // Spring Boot Test
                || className.startsWith("io.kotest") // Kotest
                || className.startsWith("io.kotlintest") // KotlinTest (legacy)
                || className.startsWith("org.scalatest") // ScalaTest
                || className.startsWith("org.jbehave") // JBehave
                || className.startsWith("org.easymock") // EasyMock
                || className.startsWith("org.powermock") // PowerMock
                || className.startsWith("org.spekframework") // Spek
                || className.startsWith("net.jqwik") // Jqwik
                || className.startsWith("org.quicktheories") // QuickTheories
                || className.startsWith("fit.") // FitNesse
                || className.startsWith("fitnesse.") // FitNesse
                || className.startsWith("org.concordion") // Concordion
                || className.startsWith("com.consol.citrus") // Citrus
                || className.startsWith("org.pitest") // PIT Mutation Testing
                || className.startsWith("org.testcontainers") // Testcontainers
                || className.startsWith("org.robolectric") // Robolectric
                || className.startsWith("net.serenitybdd") // Serenity BDD
                || className.startsWith("geb.") // Geb
                || className.startsWith("cucumber.api") // Cucumber-JVM (legacy)
                || className.startsWith("org.jmock") // JMock
                || className.startsWith("mockit") // JMockit
                || className.startsWith("play.test") // Play Framework Test
                || className.startsWith("com.codeborne.selenide") // Selenide
                || className.startsWith("org.openqa.selenium") // Selenium
                || className.startsWith("junitparams") // JUnitParams
                || className.startsWith("org.testfx") // TestFX
                || className.startsWith("com.intellij") // IntelliJ IDEA runner
                || className.startsWith("org.gradle.api.internal.tasks.testing"); // Gradle test runner
    }
}

