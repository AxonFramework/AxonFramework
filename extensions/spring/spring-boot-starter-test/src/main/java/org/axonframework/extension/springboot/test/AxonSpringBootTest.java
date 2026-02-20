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

package org.axonframework.extension.springboot.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.env.Environment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that combines {@link SpringBootTest @SpringBootTest} with the Axon test infrastructure provided by
 * {@link AxonTestConfiguration}.
 * <p>
 * Annotating a test class with {@code @AxonSpringBootTest} will:
 * <ul>
 *     <li>Bootstrap a full Spring Boot application context (same as {@code @SpringBootTest}).</li>
 *     <li>Register a {@link org.axonframework.test.fixture.MessagesRecordingConfigurationEnhancer} so that commands
 *     and events are recorded during the test.</li>
 *     <li>Expose an {@link org.axonframework.test.fixture.AxonTestFixture} bean that can be injected directly into
 *     the test constructor or fields.</li>
 * </ul>
 * <p>
 * The fixture's {@link org.axonframework.test.fixture.AxonTestFixture.Customization} can be overridden by declaring
 * a bean of that type in the test (e.g. via an inner {@code @TestConfiguration} class).
 * <p>
 * All {@link SpringBootTest} attributes (properties, classes, webEnvironment, etc.) are supported through
 * {@link AliasFor}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @AxonSpringBootTest
 * class MySliceTest {
 *
 *     @Autowired
 *     AxonTestFixture fixture;
 *
 *     @Test
 *     void myTest() {
 *         fixture.given().noPriorActivity()
 *                .when().command(new MyCommand())
 *                .then().events(new MyEvent());
 *     }
 * }
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see AxonTestConfiguration
 * @see org.axonframework.test.fixture.AxonTestFixture
 * @since 5.0.3
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(AxonTestConfiguration.class)
public @interface AxonSpringBootTest {

    /**
     * Alias for {@link SpringBootTest#properties()}.
     *
     * @return the properties to apply
     * @see SpringBootTest#value()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "value")
    String[] value() default {};

    /**
     * Properties in form {@literal key=value} that should be added to the Spring {@link Environment} before the test
     * runs.
     *
     * @return the properties to add
     * @see SpringBootTest#properties()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};

    /**
     * Application arguments that should be passed to the application under test.
     *
     * @return the application arguments to pass to the application under test
     * @see SpringBootTest#args()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "args")
    String[] args() default {};

    /**
     * The <em>component classes</em> to use for loading an {@link ApplicationContext}. If no explicit classes are
     * defined, the test will look for nested {@link Configuration @Configuration} classes before falling back to a
     * {@link SpringBootConfiguration @SpringBootConfiguration} search.
     *
     * @return the component classes used to load the application context
     * @see SpringBootTest#classes()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "classes")
    Class<?>[] classes() default {};

    /**
     * The type of web environment to create when applicable. Defaults to {@link SpringBootTest.WebEnvironment#MOCK}.
     *
     * @return the type of web environment
     * @see SpringBootTest#webEnvironment()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    /**
     * The type of main method usage to employ when creating the {@link SpringApplication} under test. Defaults to
     * {@link SpringBootTest.UseMainMethod#NEVER}.
     *
     * @return the type of main method usage
     * @see SpringBootTest#useMainMethod()
     */
    @AliasFor(annotation = SpringBootTest.class, attribute = "useMainMethod")
    SpringBootTest.UseMainMethod useMainMethod() default SpringBootTest.UseMainMethod.NEVER;
}
