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

package org.axonframework.common;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.DependencyRules;
import com.tngtech.archunit.library.dependencies.SliceRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.*;

/**
 * Verifies architectural rules for this module.
 * <p>
 * It checks for:
 * <li>Cycles between packages, to keep packages focused on a single purpose
 * and to make it easier to later move packages to new or other modules</li>
 * <li>Hierarchy violations in packages, where more specialized packages
 * refer to more general packages. This rules encourages to keep API and implementation in separate package branches so
 * they may be split into API and implementation modules at a future time.</li>
 *
 * @author John Hendrikx
 */
@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE_NAME)
public class ArchitectureTest {

    static final String BASE_PACKAGE_NAME = "org.axonframework.common";
    static final Set<Map.Entry<String, String>> IGNORED_CYCLES = parseIgnoredCycles();


    /**
     * Important: This module is not cycle free and cannot be made cycle free without introducing API breaking changes
     * (class repackaging). So this test "froze" the state of the failing test at the moment it was introduced.
     * We cannot introduce new cycles, but current ones remain present and are ignored by this test.
     * The text file `resources/archunit_store/package-cycles.txt` contains the list of failing cycles (the output
     * of the test), this is why we manually parse the file and only
     * ignore the dependencies.
     */
    @ArchTest
    private final ArchRule packagesShouldBeFreeOfCycles = filterIgnoredDependencies(
            slices()
                    .matching("(**)")
                    .should()
                    .beFreeOfCycles()
    ).as("Package Cycles");

    @ArchTest
    private final ArchRule noClassesShouldDependOnUpperPackages = FreezingArchRule.freeze(
            DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES
                    .as("Package Hierarchy Violations")
    );

    @Test
    void shouldMatchPackageName() {
        assertThat(BASE_PACKAGE_NAME).isEqualTo(MethodHandles.lookup().lookupClass().getPackageName());
    }

    private static SliceRule filterIgnoredDependencies(SliceRule rule) {
        for (Map.Entry<String, String> entry : IGNORED_CYCLES) {
            rule = rule.ignoreDependency(entry.getKey(), entry.getValue());
        }
        return rule;
    }

    /**
     * Uses the frozen archunit rule to read the pairs of class dependencies that should be ignored.
     *
     * @return entries of ignored cycles
     */
    private static Set<Map.Entry<String, String>> parseIgnoredCycles() {
        final Pattern classPattern = Pattern.compile("<([^>]+)>");
        List<Map.Entry<String, String>> ignoredCycles = new ArrayList<>();
        try (var lines = Files.lines(Paths.get(Objects.requireNonNull(ArchitectureTest.class.getResource(
                "/archunit_store/package-cycles.txt")).toURI()))) {
            lines.map(String::trim)
                 .filter(line -> line.startsWith("-"))
                 .forEach(line -> {
                     Matcher matcher = classPattern.matcher(line);
                     List<String> classes = new ArrayList<>();
                     while (matcher.find()) {
                         String className = matcher.group(1);
                         if (className.contains("(")) {
                             className = className.substring(0, className.indexOf('('));
                         }
                         if (className.contains(".")) {
                             String lastPart = className.substring(className.lastIndexOf('.') + 1);
                             if (Character.isLowerCase(lastPart.charAt(0))) {
                                 className = className.substring(0, className.lastIndexOf('.'));
                             }
                         }
                         classes.add(className);
                     }
                     if (classes.size() >= 2) {
                         ignoredCycles.add(Map.entry(classes.get(0), classes.get(1)));
                     }
                 });
        } catch (IOException | URISyntaxException e) {
            // Ignore
        }
        return Set.copyOf(ignoredCycles);
    }
}
