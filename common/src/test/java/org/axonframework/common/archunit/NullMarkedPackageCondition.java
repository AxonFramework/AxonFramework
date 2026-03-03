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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.NullMarked;


import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * For a given {@link JavaClass}, verifies it resides in a package marked with @NullMarked.
 * Therefor the {@link com.tngtech.archunit.core.domain.JavaPackage} must contain the {@code package-info.java} file
 * which must be annotated with {@code @NullMarked}.
 * <p>
 * Note: an existing {@code package-info.java} file without any relevant annotations is ignored by the compiler,
 * so we cannot reliably detect the absence of the {@code package-info.java} file.
 */
public class NullMarkedPackageCondition extends ArchCondition<JavaClass> {

    // only report each package once
    private final Set<String> checkedPackages = new HashSet<>();

    public NullMarkedPackageCondition() {
        super("reside in a package marked with @NullMarked");
    }

    @Override
    public void check(JavaClass item, ConditionEvents events) {
        String pkg = item.getPackageName();

        // only report each package once
        if (!checkedPackages.add(pkg)) {
            return;
        }

        Optional<JavaClass> packageInfo = item.getPackage().getClasses().stream()
                                              .filter(AxonFrameworkArchRules::isPackageInfo)
                                              .findFirst();

        if (packageInfo.map(it -> !it.isAnnotatedWith(NullMarked.class)).orElse(true)) {

            events.add(SimpleConditionEvent.violated(
                    item.getPackage(),
                    "Package '" + pkg + "' must declare @NullMarked in package-info.java"
            ));
        }
    }
}
