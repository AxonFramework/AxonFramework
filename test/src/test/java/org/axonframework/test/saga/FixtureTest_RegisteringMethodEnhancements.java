/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.junit.jupiter.api.*;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.test.matchers.Matchers.listWithAnyOf;
import static org.axonframework.test.matchers.Matchers.predicate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test class is intended to test whether the registration of a {@link org.axonframework.messaging.annotation.HandlerDefinition}
 * and a {@link org.axonframework.messaging.annotation.HandlerEnhancerDefinition} go according to plan.
 *
 * @author Steven van Beelen
 */
public class FixtureTest_RegisteringMethodEnhancements {

    private static final String TEST_AGGREGATE_IDENTIFIER = "aggregate-identifier";

    private FixtureConfiguration testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new SagaTestFixture<>(StubSaga.class);
    }

    @Test
    void registerParameterResolverFactory() {
        testSubject.registerParameterResolverFactory(new TestParameterResolverFactory(new AtomicBoolean(false)))
                   .givenAggregate(TEST_AGGREGATE_IDENTIFIER)
                   .published(new TriggerSagaStartEvent(TEST_AGGREGATE_IDENTIFIER))
                   .whenPublishingA(new ParameterResolvedEvent(TEST_AGGREGATE_IDENTIFIER))
                   .expectDispatchedCommandsMatching(listWithAnyOf(predicate(commandMessage -> {
                       Object payload = commandMessage.getPayload();
                       assertTrue(payload instanceof ResolveParameterCommand);
                       AtomicBoolean assertion = ((ResolveParameterCommand) payload).getAssertion();
                       return assertion.get();
                   })));
    }


    @Test
    void testRegisterParameterResolverFactoryStillCallsMetadataValue() {
        testSubject.registerParameterResolverFactory(new TestParameterResolverFactory(new AtomicBoolean(false)))
                   .givenAggregate(TEST_AGGREGATE_IDENTIFIER)
                   .published(GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(TEST_AGGREGATE_IDENTIFIER)).withMetaData(
                           Collections.singletonMap("extraIdentifier", "myExtraIdentifier")))
                   .whenPublishingA(new ParameterResolvedEvent(TEST_AGGREGATE_IDENTIFIER))
                .expectAssociationWith("extraIdentifier", "myExtraIdentifier");
    }

    @Test
    void createHandlerMethodIsCalledForRegisteredCustomHandlerDefinition() {
        AtomicBoolean handlerDefinitionReached = new AtomicBoolean(false);

        testSubject.registerHandlerDefinition(new TestHandlerDefinition(handlerDefinitionReached))
                   .givenNoPriorActivity()
                   .whenPublishingA(new TriggerSagaStartEvent(TEST_AGGREGATE_IDENTIFIER))
                   .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent.class);

        assertTrue(handlerDefinitionReached.get());
    }

    @Test
    void wrapHandlerMethodIsCalledForRegisteredCustomHandlerEnhancerDefinition() {
        AtomicBoolean handlerEnhancerReached = new AtomicBoolean(false);

        testSubject.registerHandlerEnhancerDefinition(new TestHandlerEnhancerDefinition(handlerEnhancerReached))
                   .givenNoPriorActivity()
                   .whenPublishingA(new TriggerSagaStartEvent(TEST_AGGREGATE_IDENTIFIER))
                   .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent.class);

        assertTrue(handlerEnhancerReached.get());
    }

    private static class TestParameterResolverFactory
            implements ParameterResolverFactory, ParameterResolver<AtomicBoolean> {

        private final AtomicBoolean assertion;

        private TestParameterResolverFactory(AtomicBoolean assertion) {
            this.assertion = assertion;
        }

        @Override
        public ParameterResolver<AtomicBoolean> createInstance(Executable executable,
                                                               Parameter[] parameters,
                                                               int parameterIndex) {
            return AtomicBoolean.class.equals(parameters[parameterIndex].getType()) ? this : null;
        }

        @Override
        public AtomicBoolean resolveParameterValue(Message<?> message) {
            return assertion;
        }

        @Override
        public boolean matches(Message<?> message) {
            return message.getPayloadType().isAssignableFrom(ParameterResolvedEvent.class);
        }
    }

    private static class TestHandlerDefinition implements HandlerDefinition {

        private final AtomicBoolean assertion;

        public TestHandlerDefinition(AtomicBoolean assertion) {
            this.assertion = assertion;
        }

        @Override
        public <T> Optional<MessageHandlingMember<T>> createHandler(@Nonnull Class<T> declaringType,
                                                                    @Nonnull Method method,
                                                                    @Nonnull ParameterResolverFactory parameterResolverFactory,
                                                                    Function<Object, CompletableFuture<?>> returnTypeConverter) {
            assertion.set(true);
            // We do not care about a specific MessageHandlingMember,
            //  only that this method is called to ensure its part of the FixtureConfiguration.
            return Optional.empty();
        }
    }

    private static class TestHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

        private final AtomicBoolean assertion;

        private TestHandlerEnhancerDefinition(AtomicBoolean assertion) {
            this.assertion = assertion;
        }

        @Override
        public @Nonnull
        <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
            assertion.set(true);
            // We do not care about a specific MessageHandlingMember,
            //  only that this method is called to ensure its part of the FixtureConfiguration.
            return original;
        }
    }
}
