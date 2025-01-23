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

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.junit.jupiter.api.*;
import org.mockito.internal.util.collections.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating an annotated {@link AggregateModel} with {@link AggregateMember}s on the root level of a
 * polymorphic aggregate behaves as desired when a metamodel is created with the
 * {@link AnnotatedAggregateMetaModelFactory}.
 * <p>
 * The hierarchy of the Aggregate, is as follows:
 * <p>
 *      +---------------------------+
 *      |       Root Aggregate      |
 *      |   +-------------------+   |
 *      |   |EventHandlingMember|   |
 *      |   +-------------------+   |
 *      +---------------------------+
 *                   v
 *     +-----------------------------+
 *     |        Node Aggregate       |
 *     |   +---------------------+   |
 *     |   |CommandHandlingMember|   |
 *     |   +---------------------+   |
 *     +-----------------------------+
 *        v                       v
 * +------+-------+ +-------------+------+
 * |Leaf Aggregate| |Other Leaf Aggregate|
 * +--------------+ +--------------------+
 * <p>
 * <b>Event Sourcing Handler Tests</b>
 * <p>
 * There are two tests for event sourcing handling in place, namely
 * {@link #createAggregateModelDoesNotDuplicateRootLevelAggregateMembers()} and
 * {@link #createAggregateModelDoesNotDuplicateRootLevelAggregateMembersForPolymorphicAggregates()}.
 * <p>
 * On all levels an {@link AggregateCreatedEvent} handler is present. Only the {@link EventHandlingMember} has the
 * {@link MemberEvent} handler. In such a set-up we would assume the {@link AggregateCreatedEvent} handler to be invoked
 * once in the aggregate root (which encompasses the {@link RootAggregate}, {@link NodeAggregate} and
 * {@link LeafAggregate}/{@link OtherLeafAggregate} aggregate) and once in the {@link EventHandlingMember}. Furthermore
 * we would anticipate the {@link MemberEvent} handler to be invoked once too, since there only is a single occurrence
 * of the member in the entire set up.
 * <p>
 * <b>Command Handling and Intercepting Tests</b>
 * <p>
 * There are four tests for command handling and intercepting in place. Two for command handling and two for command
 * intercepting, subdivided in an Aggregate hierarchy and a polymorphic Aggregate test cases.
 * <p>
 * A {@link CommandHandlingMember} resides on the {@link NodeAggregate}. When a {@link MemberCommand} is dispatched to a
 * leaf implementation (e.g. the {@link LeafAggregate}) it is anticipated that the {@link CommandHandlingMember} will be
 * invoked accordingly, regardless of aggregate hierarchy or use of polymorphism.
 * <p>
 * Furthermore the {@link MemberCommand} has an interceptor present on each level of the aggregate root (thus the
 * {@link RootAggregate}, {@link NodeAggregate} and {@link LeafAggregate}/{@link OtherLeafAggregate} aggregate).
 * Regardless of the handled message type by a {@link CommandHandlerInterceptor} annotated method, any matching
 * interceptors will be invoked on any level of the aggregate's hierarchy. As such the number of invocations on the
 * command interceptor for the {@link MemberCommand} should be three, matching the number of interceptor methods.
 *
 * @author Steven van Beelen
 */
class AnnotatedRootMessageHandlingMemberAggregateMetaModelFactoryTest {

    private static final MessageType TEST_COMMAND_TYPE = new MessageType("command");
    private static final AggregateCreatedEvent AGGREGATE_EVENT = new AggregateCreatedEvent("some-id");
    private static final MemberCommand MEMBER_COMMAND = new MemberCommand("some-id");

    private AtomicInteger aggregateEventHandlingCounter;
    private AtomicInteger memberEventHandlingCounter;
    private AtomicBoolean memberCommandHandlingValidator;
    private AtomicInteger memberCommandInterceptingCounter;

    @BeforeEach
    void setUp() {
        aggregateEventHandlingCounter = new AtomicInteger(0);
        memberEventHandlingCounter = new AtomicInteger(0);
        memberCommandHandlingValidator = new AtomicBoolean(false);
        memberCommandInterceptingCounter = new AtomicInteger(0);
    }

    @AfterEach
    void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void createAggregateModelDoesNotDuplicateRootLevelAggregateMembers() {
        int expectedNumberOfAggregateEventHandlerInvocations = 2;
        int expectedNumberOfMemberEventHandlerInvocations = 1;

        EventMessage<AggregateCreatedEvent> testAggregateEvent = EventTestUtils.asEventMessage(AGGREGATE_EVENT);
        EventMessage<MemberEvent> testMemberEvent = EventTestUtils.asEventMessage(new MemberEvent());
        LeafAggregate testModel = new LeafAggregate();

        AggregateModel<LeafAggregate> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(LeafAggregate.class);

        testSubject.publish(testAggregateEvent, testModel);
        assertEquals(expectedNumberOfAggregateEventHandlerInvocations, aggregateEventHandlingCounter.get());
        testSubject.publish(testMemberEvent, testModel);
        assertEquals(expectedNumberOfMemberEventHandlerInvocations, memberEventHandlingCounter.get());
    }

    @Test
    void createAggregateModelDoesNotDuplicateRootLevelAggregateMembersForPolymorphicAggregates() {
        int expectedNumberOfAggregateEventHandlerInvocations = 2;
        int expectedNumberOfMemberEventHandlerInvocations = 1;

        EventMessage<AggregateCreatedEvent> testAggregateEvent = EventTestUtils.asEventMessage(AGGREGATE_EVENT);
        EventMessage<MemberEvent> testMemberEvent = EventTestUtils.asEventMessage(new MemberEvent());
        LeafAggregate testModel = new LeafAggregate();

        //noinspection unchecked
        Set<Class<? extends RootAggregate>> subtypes = Sets.newSet(LeafAggregate.class, OtherLeafAggregate.class);
        AggregateModel<RootAggregate> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subtypes);

        testSubject.publish(testAggregateEvent, testModel);
        assertEquals(expectedNumberOfAggregateEventHandlerInvocations, aggregateEventHandlingCounter.get());
        testSubject.publish(testMemberEvent, testModel);
        assertEquals(expectedNumberOfMemberEventHandlerInvocations, memberEventHandlingCounter.get());
    }

    @Test
    void createdAggregateModelReturnsCommandHandlingFunctionFromParentAggregate() throws Exception {
        CommandMessage<MemberCommand> testMemberCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, MEMBER_COMMAND);
        DefaultUnitOfWork.startAndGet(testMemberCommand);

        LeafAggregate testAggregate = new LeafAggregate();
        AggregateModel<LeafAggregate> testModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(LeafAggregate.class);

        AnnotatedAggregate<LeafAggregate> testSubject =
                AnnotatedAggregate.initialize(testAggregate, testModel, mock(EventBus.class));

        testSubject.handle(testMemberCommand);
        assertTrue(memberCommandHandlingValidator.get());
    }

    @Test
    void createdAggregateModelReturnsCommandHandlingFunctionFromParentAggregateForPolymorphicAggregate()
            throws Exception {
        CommandMessage<MemberCommand> testMemberCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, MEMBER_COMMAND);
        DefaultUnitOfWork.startAndGet(testMemberCommand);

        LeafAggregate testAggregate = new LeafAggregate();
        //noinspection unchecked
        Set<Class<? extends RootAggregate>> subtypes = Sets.newSet(LeafAggregate.class, OtherLeafAggregate.class);
        AggregateModel<RootAggregate> testModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subtypes);

        AnnotatedAggregate<RootAggregate> testSubject =
                AnnotatedAggregate.initialize(testAggregate, testModel, mock(EventBus.class));


        testSubject.handle(testMemberCommand);
        assertTrue(memberCommandHandlingValidator.get());
    }

    @Test
    void createdAggregateModelInvokesAllCommandInterceptors() throws Exception {
        int expectedNumberOfMemberCommandInterceptorInvocations = 3;

        CommandMessage<MemberCommand> testMemberCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, MEMBER_COMMAND);
        DefaultUnitOfWork.startAndGet(testMemberCommand);

        LeafAggregate testAggregate = new LeafAggregate();
        AggregateModel<LeafAggregate> testModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(LeafAggregate.class);

        AnnotatedAggregate<LeafAggregate> testSubject =
                AnnotatedAggregate.initialize(testAggregate, testModel, mock(EventBus.class));

        testSubject.handle(testMemberCommand);
        assertEquals(expectedNumberOfMemberCommandInterceptorInvocations, memberCommandInterceptingCounter.get());
    }

    @Test
    void createdAggregateModelInvokesAllCommandInterceptorsForPolymorphicAggregate() throws Exception {
        int expectedNumberOfMemberCommandInterceptorInvocations = 3;
        CommandMessage<MemberCommand> testMemberCommand =
                new GenericCommandMessage<>(TEST_COMMAND_TYPE, MEMBER_COMMAND);
        DefaultUnitOfWork.startAndGet(testMemberCommand);

        LeafAggregate testAggregate = new LeafAggregate();
        //noinspection unchecked
        Set<Class<? extends RootAggregate>> subtypes = Sets.newSet(LeafAggregate.class, OtherLeafAggregate.class);
        AggregateModel<RootAggregate> testModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subtypes);

        AnnotatedAggregate<RootAggregate> testSubject =
                AnnotatedAggregate.initialize(testAggregate, testModel, mock(EventBus.class));


        testSubject.handle(testMemberCommand);
        assertEquals(expectedNumberOfMemberCommandInterceptorInvocations, memberCommandInterceptingCounter.get());
    }

    @SuppressWarnings("unused")
    private abstract class RootAggregate {

        @AggregateMember
        private final EventHandlingMember eventHandlingMember = new EventHandlingMember();

        @CommandHandlerInterceptor
        public void intercept(MemberCommand command) {
            memberCommandInterceptingCounter.incrementAndGet();
        }

        @EventHandler
        public void on(AggregateCreatedEvent event) {
            aggregateEventHandlingCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private abstract class NodeAggregate extends RootAggregate {

        @AggregateMember
        private final CommandHandlingMember commandHandlingMember = new CommandHandlingMember();

        @CommandHandlerInterceptor
        public void intercept(MemberCommand command) {
            memberCommandInterceptingCounter.incrementAndGet();
        }

        @EventHandler
        public void on(AggregateCreatedEvent event) {
            aggregateEventHandlingCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private class LeafAggregate extends NodeAggregate {

        public LeafAggregate() {
            // Normally required no-arg constructor
        }

        @CommandHandlerInterceptor
        public void intercept(MemberCommand command) {
            memberCommandInterceptingCounter.incrementAndGet();
        }

        @CommandHandler
        public void handle(CreateAggregateCommand command) {
            // Here to unsure several levels of the aggregate have command handlers.
        }

        @EventHandler
        public void on(AggregateCreatedEvent event) {
            aggregateEventHandlingCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private class OtherLeafAggregate extends NodeAggregate {

        public OtherLeafAggregate() {
            // Normally required no-arg constructor
        }

        @CommandHandlerInterceptor
        public void intercept(MemberCommand command) {
            memberCommandInterceptingCounter.incrementAndGet();
        }

        @CommandHandler
        public void handle(CreateAggregateCommand command) {
            // Here to unsure several levels of the aggregate have command handlers.
        }

        @EventHandler
        public void on(AggregateCreatedEvent event) {
            aggregateEventHandlingCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private class EventHandlingMember {

        @EventHandler
        public void on(AggregateCreatedEvent event) {
            aggregateEventHandlingCounter.incrementAndGet();
        }

        @EventHandler
        public void on(MemberEvent event) {
            memberEventHandlingCounter.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    private class CommandHandlingMember {

        @CommandHandler
        public void handle(MemberCommand command) {
            memberCommandHandlingValidator.set(true);
        }
    }

    public record CreateAggregateCommand(String id) {

    }

    public record AggregateCreatedEvent(String id) {

    }

    public record MemberCommand(@TargetAggregateIdentifier String id) {

        @Override
        public String id() {
                return id;
            }
        }

    public static class MemberEvent {

    }
}
