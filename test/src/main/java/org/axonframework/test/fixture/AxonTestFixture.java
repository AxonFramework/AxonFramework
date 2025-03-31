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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.configuration.NewConfiguration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.test.aggregate.Reporter;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.IgnoreField;
import org.axonframework.test.matchers.MapEntryMatcher;
import org.axonframework.test.matchers.MatchAllFieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.matchers.PayloadMatcher;
import org.axonframework.test.saga.CommandValidator;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.axonframework.test.matchers.Matchers.deepEquals;
import static org.hamcrest.CoreMatchers.*;

/**
 * Fixture for testing Axon Framework application. The fixture can be configured to use your whole application
 * configuration or just a portion of that (single module or component). The fixture allows the execution of
 * given-when-then style.
 *
 * @author Allard Buijze
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class AxonTestFixture implements AxonTestPhase.Setup {

    private final NewConfiguration configuration;
    private final Customization customization;
    private final MessageTypeResolver messageTypeResolver;
    private final RecordingCommandBus commandBus;
    private final RecordingEventSink eventSink;

    private AxonTestFixture(NewConfiguration configuration, UnaryOperator<Customization> customization) {
        this.customization = customization.apply(new Customization());
        this.configuration = configuration;
        this.messageTypeResolver = configuration.getComponent(MessageTypeResolver.class);
        this.commandBus = (RecordingCommandBus) configuration.getComponent(CommandBus.class);
        this.eventSink = (RecordingEventSink) configuration.getComponent(EventSink.class);
    }

    public static AxonTestPhase.Setup with(ApplicationConfigurer<?> configurer) {
        return with(configurer, c -> c);
    }

    public static AxonTestPhase.Setup with(ApplicationConfigurer<?> configurer,
                                           UnaryOperator<Customization> customization) {
        var testConfigurer = new TestApplicationConfigurer(configurer);
        var configuration = testConfigurer.build();
        return with(configuration, customization);
    }

    public static AxonTestPhase.Setup with(TestApplicationConfigurer configurer) {
        var configuration = configurer.build();
        return with(configuration, c -> c);
    }

    public static AxonTestPhase.Setup with(TestApplicationConfigurer configurer,
                                           UnaryOperator<Customization> customization) {
        var configuration = configurer.build();
        return with(configuration, customization);
    }

    public static AxonTestPhase.Setup with(NewConfiguration configuration) {
        return new AxonTestFixture(configuration, c -> c);
    }

    public static AxonTestPhase.Setup with(NewConfiguration configuration, UnaryOperator<Customization> customization) {
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

    public record Customization(List<FieldFilter> fieldFilters) {

        public Customization() {
            this(new ArrayList<>());
        }

        public Customization registerFieldFilter(FieldFilter fieldFilter) {
            this.fieldFilters.add(fieldFilter);
            return this;
        }

        public Customization registerIgnoredField(Class<?> declaringClass, String fieldName) {
            return registerFieldFilter(new IgnoreField(declaringClass, fieldName));
        }

        @Override
        public List<FieldFilter> fieldFilters() {
            return fieldFilters;
        }
    }
}
