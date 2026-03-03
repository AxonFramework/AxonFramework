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

package org.axonframework.common.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.axonframework.common.archunit.AxonFrameworkArchRules.arePackageInfo;

import java.util.HashSet;
import java.util.Set;


import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.NullMarked;

public interface PackageInfoConvention {

    /**
     * Verifies that all packages have a package-info.java file.
     *
     * @param classes the classes to check, use {@link com.tngtech.archunit.junit.AnalyzeClasses} to configure
     */
    @ArchTest
    default void allPackagesMustHaveAPackageInfo(final JavaClasses classes) {
        classes()
                .should(havePackageInfo())
                .check(classes);
    }

    @ArchTest
    default void packageInfosMustBeAnnotatedWithNullMarked(final JavaClasses classes) {
        classes().that(arePackageInfo())
                .should()
                .beAnnotatedWith(NullMarked.class)
                .check(classes);
    }

    private static ArchCondition<JavaClass> havePackageInfo() {
        return new ArchCondition<>("reside in a package with a package-info.java") {
            // only report each package once
            private final Set<String> reportedPackages = new HashSet<>();

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                final String pkg = item.getPackageName();

                if (reportedPackages.contains(pkg)) {
                    return;
                }

                boolean hasPackageInfo = item.getPackage().getClasses().stream()
                                             .anyMatch(AxonFrameworkArchRules::isPackageInfo);

                if (!hasPackageInfo) {
                    reportedPackages.add(pkg);
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "Package '" + pkg + "' has no package-info.java"
                    ));
                }
            }
        };
    }
}
