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

public final class AxonFrameworkArchRules {



    public static final ArchRule CLASSES_MUST_RESIDE_IN_NULL_MARKED_PACKAGE = classes()
            .should(new NullMarkedPackageCondition());

    public static DescribedPredicate<JavaClass> arePackageInfo() {
        return new DescribedPredicate<>("are package-info classes") {
            @Override
            public boolean test(JavaClass javaClass) {
                return isPackageInfo(javaClass);
            }
        };
    }

    public static boolean isPackageInfo(JavaClass c) {
        return "package-info".equals(c.getSimpleName()) || c.getName().endsWith(".package-info");
    }

    private AxonFrameworkArchRules() {
        // do not instantiate
    }
}
