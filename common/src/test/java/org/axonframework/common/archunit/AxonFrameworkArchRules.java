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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;


import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * A collection of ArchUnit rules for Axon Framework, aimed to be used in every module.
 */
public final class AxonFrameworkArchRules {

    /**
     * Defines an architectural rule that verifies all classes must reside in a package
     * marked with the {@code @NullMarked} annotation.
     * <p>
     * The rule checks whether the {@code package-info.java} file for the package is present
     * and annotated with {@code @NullMarked}. If a package does not meet this condition,
     * it violates the rule.
     * <p>
     * This rule ensures that nullability conventions are enforced consistently
     * across all packages in the codebase. It relies on the {@link NullMarkedPackageCondition}
     * to evaluate if a package satisfies the rule.
     */
    public static final ArchRule CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE = classes()
            .should(new NullMarkedPackageCondition());

    /**
     * Creates a {@link DescribedPredicate} that checks if a {@link JavaClass} represents
     * a package-info class.
     *
     * @return a predicate that evaluates to true if a class is a package-info class, false otherwise.
     */
    public static DescribedPredicate<JavaClass> arePackageInfo() {
        return new DescribedPredicate<>("are package-info classes") {
            @Override
            public boolean test(JavaClass javaClass) {
                return isPackageInfo(javaClass);
            }
        };
    }

    /**
     * Checks if a JavaClass is a package-info class.
     * @param c The JavaClass to check.
     * @return True if the class is a package-info class, false otherwise.
     */
    public static boolean isPackageInfo(JavaClass c) {
        return "package-info".equals(c.getSimpleName()) || c.getName().endsWith(".package-info");
    }

    private AxonFrameworkArchRules() {
        // do not instantiate
    }
}
