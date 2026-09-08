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

package org.axonframework.test.saga;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.test.fixture.AxonTestPhase;
import org.axonframework.test.fixture.CommandValidator;
import org.axonframework.test.matchers.FieldFilter;
import org.axonframework.test.matchers.Matchers;
import org.hamcrest.Matcher;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link FixtureExecutionResult}, asserting against the {@link AxonTestPhase.Then then-phase}
 * the "when" phase produced.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @since 5.4.0
 */
class FixtureExecutionResultImpl implements FixtureExecutionResult {

    private final Class<?> sagaType;
    private final AxonTestPhase.Then.Event then;
    private final CommandValidator commandValidator;
    private final EventValidator eventValidator;

    /**
     * Constructs a {@code FixtureExecutionResultImpl} asserting on the given {@code then} phase.
     *
     * @param sagaType    the type of Saga under test, used to filter the store on the association assertions
     * @param then        the then-phase of the fixture the Saga was driven through
     * @param fieldFilter the filter describing the fields to include when comparing messages
     */
    FixtureExecutionResultImpl(Class<?> sagaType, AxonTestPhase.Then.Event then, FieldFilter fieldFilter) {
        this.sagaType = Objects.requireNonNull(sagaType, "The sagaType may not be null.");
        this.then = Objects.requireNonNull(then, "The then-phase may not be null.");
        Objects.requireNonNull(fieldFilter, "The fieldFilter may not be null.");
        this.commandValidator = new CommandValidator(this::dispatchedCommands, () -> {}, fieldFilter);
        this.eventValidator = new EventValidator(this::publishedEvents, fieldFilter);
    }

    @Override
    public FixtureExecutionResult expectActiveSagas(int expected) {
        then.expect(SagaAssertions.activeSagas(expected));
        return this;
    }

    @Override
    public FixtureExecutionResult expectAssociationWith(String associationKey, Object associationValue) {
        then.expect(SagaAssertions.associationWith(sagaType, associationKey, associationValue));
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoAssociationWith(String associationKey, Object associationValue) {
        then.expect(SagaAssertions.noAssociationWith(sagaType, associationKey, associationValue));
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommands(Object... commands) {
        commandValidator.assertDispatchedEqualTo(commands);
        return this;
    }

    @Override
    public FixtureExecutionResult expectDispatchedCommandsMatching(
            Matcher<? extends List<? super CommandMessage>> matcher
    ) {
        commandValidator.assertDispatchedMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectNoDispatchedCommands() {
        commandValidator.assertDispatchedMatching(Matchers.noCommands());
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEvents(Object... expected) {
        eventValidator.assertPublishedEvents(expected);
        return this;
    }

    @Override
    public FixtureExecutionResult expectPublishedEventsMatching(
            Matcher<? extends List<? super EventMessage>> matcher
    ) {
        eventValidator.assertPublishedEventsMatching(matcher);
        return this;
    }

    @Override
    public FixtureExecutionResult expectSuccessfulHandlerExecution() {
        then.success();
        return this;
    }

    /**
     * The commands the then-phase recorded, reached through its own assertion so the exclusion of the commands the
     * "when" phase dispatched itself is applied here too.
     */
    private List<CommandMessage> dispatchedCommands() {
        AtomicReference<List<CommandMessage>> captured = new AtomicReference<>();
        then.commandsSatisfy(captured::set);
        return captured.get();
    }

    /**
     * The events the then-phase recorded, reached through its own assertion so the exclusion of the event the "when"
     * phase published itself is applied here too.
     */
    private List<EventMessage> publishedEvents() {
        AtomicReference<List<EventMessage>> captured = new AtomicReference<>();
        then.eventsSatisfy(captured::set);
        return captured.get();
    }
}
