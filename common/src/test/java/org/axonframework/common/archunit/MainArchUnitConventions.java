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

import static org.axonframework.common.archunit.AxonFrameworkArchRules.CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE;


import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.ArchTest;

/**
 * Represents a set of ArchUnit architectural testing conventions to ensure adherence
 * to specific rules related to nullability and package structure in the application.
 * <p>
 * This interface defines methods for validating architectural constraints using the ArchUnit library.
 * Specifically, it ensures that all packages must reside in packages annotated with {@code @NullMarked}
 * to enforce consistent nullability conventions across the codebase.
 * <p>
 * Implementations of this interface can be used to define architecture tests for specific packages in the application.
 * <p>
 * Example use cases include verifying that:
 * - All packages have a {@code package-info.java} file annotated with {@code @NullMarked}.
 * - Nullability constraints are followed consistently across all defined packages.
 *
 * The testing logic leverages predefined ArchUnit rules, such as
 * {@link AxonFrameworkArchRules#CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE}, to evaluate codebase compliance.
 * <p>
 * Note that this interface is intended to be used in conjunction with ArchUnit's testing framework, and the methods
 * defined here apply to each ArchUnit test class that implements it, meaning that we can control framework wide rules
 * by adding them here.
 */
public interface MainArchUnitConventions {

    /**
     * Ensures that all packages must reside in a package annotated with {@code @NullMarked}.
     * <p>
     * This method utilizes an architectural rule defined as
     * {@link AxonFrameworkArchRules#CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE} to verify
     * that nullability conventions are enforced across all packages. The rule checks whether
     * the {@code package-info.java} file is present and properly annotated with {@code @NullMarked}.
     *
     * @param classes the set of Java classes to verify
     */
    @ArchTest
    default void allPackageMustHaveANullMarkedPackageInfo(JavaClasses classes) {
        CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE
                .because("We use NullMarked annotation to indicate nullability.")
                .check(classes);
    }
}
