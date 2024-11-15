/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.spring.serialization.avro;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;


/**
 * Configures the base packages used by autoconfiguration when scanning for classes containing Avro Schema.
 * One of {@link #basePackageClasses()}, {@link #basePackages()} or its alias
 * {@link #value()} may be specified to define specific packages to scan. If specific
 * packages are not defined scanning will occur from the package of the class with this
 * annotation.
 *
 * @see AvroSchemaPackages
 */
@Retention(RetentionPolicy.RUNTIME)
@Import(AvroSchemaPackages.Registrar.class)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface AvroSchemaScan {
    /**
     * Base packages to scan for classes containing Avro Schemas. {@link #value()} is an alias for (and mutually
     * exclusive with) this attribute.
     * <p>
     * Use {@link #basePackageClasses()} for a type-safe alternative to String-based
     * package names.
     *
     * @return the base packages to scan
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to
     * scan for classes containing Avro Schemas. The package of each class specified will be scanned.
     * <p>
     * Consider creating a special no-op marker class or interface in each package that
     * serves no purpose other than being referenced by this attribute.
     *
     * @return classes from the base packages to scan
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * Alias for the {@link #basePackages()} attribute. Allows for more concise annotation
     * declarations e.g.: {@code @AvroSchemaScan("org.my.pkg")} instead of
     * {@code @AvroSchemaScan(basePackages="org.my.pkg")}.
     *
     * @return the base packages to scan
     */
    @AliasFor("basePackages")
    String[] value() default {};
}
