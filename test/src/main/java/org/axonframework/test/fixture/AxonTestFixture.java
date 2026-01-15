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

package org.axonframework.test.fixture;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandBus;
import org.axonframework.common.configuration.ApplicationConfigurer;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.EventSink;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory;
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

    private final AxonConfiguration configuration;
    private final Customization customization;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;
    private final UnitOfWorkFactory unitOfWorkFactory;

    /**
     * Creates a new fixture.
     *
     * @param configuration The fixture will use the configuration to obtain components needed for test execution.
     * @param customization A function that allows to customize the fixture setup.
     */
    public AxonTestFixture(
            @Nonnull AxonConfiguration configuration,
            @Nonnull Customization customization
    ) {
        this.customization = Objects.requireNonNull(customization, "Customization may not be null.");
        this.configuration = Objects.requireNonNull(configuration, "Configuration may not be null.");

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

        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.unitOfWorkFactory = configuration.getComponent(UnitOfWorkFactory.class);
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
    public static AxonTestFixture with(
            @Nonnull ApplicationConfigurer configurer,
            @Nonnull UnaryOperator<Customization> customization
    ) {
        Objects.requireNonNull(configurer, "Configurer may not be null");
        Objects.requireNonNull(customization, "Customization may not be null");
        var fixtureConfiguration = customization.apply(new Customization());
        if (!fixtureConfiguration.axonServerEnabled()) {
            configurer = configurer.componentRegistry(cr -> cr.disableEnhancer(
                    "org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer"));
        }
        var configuration =
                configurer.componentRegistry(cr -> cr.registerEnhancer(new MessagesRecordingConfigurationEnhancer()))
                          .start();
        return new AxonTestFixture(configuration, fixtureConfiguration);
    }

    @Override
    public AxonTestPhase.Given given() {
        return new AxonTestGiven(
                configuration,
                customization,
                commandBus,
                eventSink,
                messageTypeResolver,
                unitOfWorkFactory
        );
    }

    @Override
    public AxonTestPhase.When when() {
        return new AxonTestWhen(
                configuration,
                customization,
                commandBus,
                eventSink,
                messageTypeResolver,
                unitOfWorkFactory
        );
    }

    @Override
    public void stop() {
        configuration.shutdown();
    }

    /**
     * Allow customizing the fixture setup.
     *
     * @param axonServerEnabled True if Axon Server should be enabled, false otherwise. It's enabled by default.
     * @param fieldFilters Collections of {@link FieldFilter FieldFilters} used to adjust the matchers for commands,
     *                     events, and result messages.
     */
    public record Customization(
            boolean axonServerEnabled,
            @Nonnull List<FieldFilter> fieldFilters
    ) {

        /**
         * Creates a new instance of {@code Customization}.
         */
        public Customization() {
            this(true, new ArrayList<>());
        }

        /**
         * Registers the given {@code fieldFilter}, which is used to define which Fields are used when comparing
         * objects.
         * <p>
         * This filter is used by the following methods:
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
            List<FieldFilter> fieldFiltersCopy = new ArrayList<>(this.fieldFilters);
            fieldFiltersCopy.add(fieldFilter);
            return new Customization(axonServerEnabled, fieldFiltersCopy);
        }

        /**
         * Indicates that a field with given {@code fieldName}, which is declared in given {@code declaringClass} is
         * ignored when performing deep equality checks.
         * <p>
         * This filter is used by the following methods:
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
        public List<FieldFilter> fieldFilters() {
            return fieldFilters;
        }

        /**
         * Configures Axon Server to be disabled.
         *
         * @return The current Customization, for fluent interfacing.
         */
        public Customization disableAxonServer() {
            return new Customization(false, fieldFilters);
        }

        /**
         * Indicates whether Axon Server is enabled.
         *
         * @return True if Axon Server is enabled, false otherwise.
         */
        public boolean axonServerEnabled() {
            return axonServerEnabled;
        }

        /**
         * Indicates whether Axon Server is disabled.
         *
         * @return True if Axon Server is disabled, false otherwise.
         */
        public boolean axonServerDisabled() {
            return! axonServerEnabled;
        }
    }
}
