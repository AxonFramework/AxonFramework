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

package org.axonframework.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker to specify the registration scope for objects that are registered in the registry.
 * <p>
 *
 * {@link Scope#CURRENT} never be copied to the child registry, if that is created. {@link Scope#ANCESTORS} will be copied
 * to the child registry (and later to its children), if that is created.
 * <br />
 * This annotation is intended to be put on {@link org.axonframework.common.configuration.ConfigurationEnhancer} and
 * {@link org.axonframework.common.configuration.DecoratorDefinition} classes to control their registration scopes.
 * </p>
 *
 * @author Simon Zambrovski
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface RegistrationScope {

    /**
     * Optional description of the reason.
     *
     * @return A description of the reason.
     */
    String value() default "";

    /**
     * Sets the registration scope for the instance.
     * @return The registration scope.
     */
    Scope registrationScope() default Scope.CURRENT;

    /**
     * Registration scope.
     */
    enum Scope {
        /**
         * Register only for current registry.
         */
        CURRENT,
        /**
         * Register for current registry, but copy to all ancestor registries, created from it.
         */
        ANCESTORS
    }
}
