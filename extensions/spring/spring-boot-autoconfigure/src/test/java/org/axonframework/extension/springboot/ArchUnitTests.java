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

package org.axonframework.extension.springboot;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public class ArchUnitTests {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void beforeAll() {
        importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("org.axonframework.extension.springboot");
    }

    @AfterAll
    static void afterAll() {
        importedClasses = null;
    }

    @Test
    void allAutoconfigurationClassesAreListedInMetaInfImports() {

        classes()
                .that()
                .areAnnotatedWith(AutoConfiguration.class)
                .should(listedInAutoConfigurationImports())
                .andShould()
                .haveSimpleNameEndingWith("AutoConfiguration")
                .check(importedClasses);
    }

    @Test
    void allClassesSuffixedWithAutoConfigurationAreAnnotated() {
        classes()
                .that()
                .haveSimpleNameEndingWith("AutoConfiguration")
                .and()
                .areNotAssignableTo(MetricsProperties.AutoConfiguration.class)
                .should()
                .beAnnotatedWith(AutoConfiguration.class)
                .check(importedClasses);
    }

    private static ArchCondition<JavaClass> listedInAutoConfigurationImports() {
        return new ArchCondition<JavaClass>(
                "be listed in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String className = javaClass.getName();
                if (!autoConfigurationImports().contains(className)) {
                    String message = String.format("AutoConfiguration %s is not listed in %s",
                                                   className,
                                                   RESOURCE_PATH);
                    events.add(SimpleConditionEvent.violated(javaClass, message));
                }
            }
        };
    }

    private static final String RESOURCE_PATH = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static java.util.Set<String> autoConfigurationImports() {
        java.util.Set<String> result = new java.util.HashSet<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ArchUnitTests.class.getClassLoader();
        }
        try (java.io.InputStream is = cl.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Could not load resource: " + RESOURCE_PATH);
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    result.add(trimmed);
                }
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error reading " + RESOURCE_PATH, e);
        }
        return result;
    }
}
