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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.test.FixtureExecutionException;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Fixture for testing Axon Framework application. The fixture can be configured to use your whole application
 * configuration or just a portion of that (single module or component). The fixture allows the execution of
 * given-when-then style.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonTestFixture implements AxonTestPhase.Setup {

    private final Configuration configuration;
    private final Customization customization;
    private final MessageTypeResolver messageTypeResolver;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;

    AxonTestFixture(@Nonnull Configuration configuration,
                    @Nonnull UnaryOperator<Customization> customization) {
        this.customization = customization.apply(new Customization());
        this.configuration = configuration;
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);

        CommandBus commandBusComponent = configuration.getComponent(CommandBus.class);
        if (!(commandBusComponent instanceof RecordingCommandBus)) {
            throw new FixtureExecutionException(
                "CommandBus is not a RecordingCommandBus. This may happen in Spring environments where the " +
                "MessagesRecordingConfigurationEnhancer is not properly registered. " +
                "Please declare MessagesRecordingConfigurationEnhancer as a bean in your test context. " +
                "Note: This configuration may be subject to change until the 5.0.0 release."
            );
        }
        this.commandBus = (RecordingCommandBus) commandBusComponent;

        // Safely cast EventSink with proper error handling
        EventSink eventSinkComponent = configuration.getComponent(EventSink.class);
        if (!(eventSinkComponent instanceof RecordingEventSink)) {
            throw new FixtureExecutionException(
                "EventSink is not a RecordingEventSink. This may happen in Spring environments where the " +
                "MessagesRecordingConfigurationEnhancer is not properly registered. " +
                "Please declare MessagesRecordingConfigurationEnhancer as a bean in your test context. " +
                "Note: This configuration may be subject to change until the 5.0.0 release."
            );
        }
        this.eventSink = (RecordingEventSink) eventSinkComponent;
    }

    /**
     * Creates a new fixture.
     *
     * @param configurer The fixture will use the configuration build from the given configurer to obtain components
     *                   needed for test execution.
     * @return A new fixture instance
     */
    public static AxonTestFixture with(@Nonnull ApplicationConfigurer configurer) {
        return with(configurer, c -> c);
    }

    /**
     * Creates a new fixture.
     *
     * @param configurer    The fixture will use the configuration build from the given configurer to obtain components
     *                      needed for test execution.
     * @param customization A function that allows to customize the fixture setup.
     * @return A new fixture instance
     */
    public static AxonTestFixture with(@Nonnull ApplicationConfigurer configurer,
                                       @Nonnull UnaryOperator<Customization> customization) {
        Objects.requireNonNull(configurer, "Configurer may not be null");
        Objects.requireNonNull(customization, "Customization may not be null");
        var configuration =
                configurer.componentRegistry(cr -> cr.registerEnhancer(new MessagesRecordingConfigurationEnhancer()))
                          .start();
        return new AxonTestFixture(configuration, customization);
    }

    @Override
    public AxonTestPhase.Given given() {
        return new AxonTestGiven(configuration, customization, commandBus, eventSink, messageTypeResolver);
    }

    @Override
    public AxonTestPhase.When when() {
        return new AxonTestWhen(configuration, customization, messageTypeResolver, commandBus, eventSink);
    }

    /**
     * Allow to customize the fixture setup.
     */
    public record Customization(List<FieldFilter> fieldFilters) {

        /**
         * Creates a new instance of {@link Customization}.
         */
        public Customization() {
            this(new ArrayList<>());
        }

        /**
         * Registers the given {@code fieldFilter}, which is used to define which Fields are used when comparing
         * objects.
         * <p>
         * This filter is used by following methods:
         * <ul>
         *     <li>{@link AxonTestPhase.Then.Message#events}</li>
         *     <li>{@link AxonTestPhase.Then.Message#commands}</li>
         *     <li>{@link AxonTestPhase.Then.Command#resultMessagePayload}</li>
         * </ul>
         * <p>
         * If you use custom assertions with methods like {@link AxonTestPhase.Then.Event#eventsSatisfy} or
         * {@link AxonTestPhase.Then.Event#eventsMatch}  this filter is not taken into account.
         * <p/>
         * When multiple filters are registered, a Field must be accepted by all registered filters in order to be
         * accepted.
         * <p/>
         * By default, all Fields are included in the comparison.
         *
         * @param fieldFilter The FieldFilter that defines which fields to include in the comparison.
         * @return the current Customization, for fluent interfacing.
         */
        public Customization registerFieldFilter(@Nonnull FieldFilter fieldFilter) {
            this.fieldFilters.add(fieldFilter);
            return this;
        }

        /**
         * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass} is
         * ignored when performing deep equality checks.
         * <p>
         * This filter is used by following methods:
         * <ul>
         *     <li>{@link AxonTestPhase.Then.Message#events}</li>
         *     <li>{@link AxonTestPhase.Then.Message#commands}</li>
         *     <li>{@link AxonTestPhase.Then.Command#resultMessagePayload}</li>
         * </ul>
         * <p>
         * If you use custom assertions with methods like {@link AxonTestPhase.Then.Event#eventsSatisfy} or
         * {@link AxonTestPhase.Then.Event#eventsMatch}  this filter is not taken into account.
         *
         * @param declaringClass The class declaring the field.
         * @param fieldName      The name of the field.
         * @return the current Customization, for fluent interfacing
         * @throws FixtureExecutionException when no such field is declared
         */
        public Customization registerIgnoredField(@Nonnull Class<?> declaringClass, @Nonnull String fieldName) {
            return registerFieldFilter(new IgnoreField(declaringClass, fieldName));
        }

        /**
         * Configured field filters.
         *
         * @return The list of field filters.
         */
        @Override
        public List<FieldFilter> fieldFilters() {
            return fieldFilters;
        }
    }
}
