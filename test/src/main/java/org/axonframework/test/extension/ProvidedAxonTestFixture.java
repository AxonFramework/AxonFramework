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

package org.axonframework.test.extension;

import org.axonframework.test.fixture.AxonTestFixture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field or method that provides the {@link AxonTestFixture}.
 * <p>
 * This annotation can be used in two ways:
 * <ul>
 *     <li>On a <b>field or method</b> (can be static) of a test class. The type or return type must be an
 *     {@link AxonTestFixtureProvider}. In this case, setting the {@link #value()} to an explicit provider implementation
 *     is forbidden as the annotated element already serves as the provider.</li>
 *     <li>On a <b>{@code @Test} method directly, or on the test class</b> itself (applying to all tests in that class).
 *     If used this way, the {@link #value()} <b>must</b> be set to a concrete {@link AxonTestFixtureProvider} implementation
 *     that the extension will use to instantiate the fixture, unless an {@link AxonTestFixtureProvider} is already
 *     declared via a field or method within the test class hierarchy.</li>
 * </ul>
 *
 * @author Jan Galinski
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProvidedAxonTestFixture {

    /**
     * The {@link AxonTestFixtureProvider} implementation to use when this annotation is placed on a test class or test
     * method.
     * <p>
     * Defaults to {@link AxonTestFixtureProvider} itself, indicating that the provider should be discovered on a field
     * or method.
     *
     * @return the provider implementation class
     */
    Class<? extends AxonTestFixtureProvider> value() default AxonTestFixtureProvider.class;
}