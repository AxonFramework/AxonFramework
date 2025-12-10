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

package org.axonframework.common;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.DependencyRules;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE_NAME)
public class ArchitectureTest {
  static final String BASE_PACKAGE_NAME = "org.axonframework.common";

  @ArchTest
  private final ArchRule packagesShouldBeFreeOfCycles = FreezingArchRule.freeze(
      slices().matching("(**)").should().beFreeOfCycles().as("Package Cycles")
  );

  @ArchTest
  private final ArchRule noClassesShouldDependOnUpperPackages = FreezingArchRule.freeze(
      DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES.as("Package Hierarchy Violations")
  );

  @Test
  void shouldMatchPackageName() {
    assertThat(BASE_PACKAGE_NAME).isEqualTo(MethodHandles.lookup().lookupClass().getPackageName());
  }
}
