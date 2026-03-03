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

public interface MainArchUnitConventions {

    @ArchTest
    default void allPackageMustHaveANullMarkedPackageInfo(JavaClasses classes) {
        CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE
                .because("We use NullMarked annotation to indicate nullability.")
                .check(classes);
    }
}
