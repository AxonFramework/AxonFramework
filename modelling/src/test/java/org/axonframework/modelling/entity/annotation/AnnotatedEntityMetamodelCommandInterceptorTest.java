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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.interception.annotation.CommandHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests that {@link CommandHandlerInterceptor} (and the more generic
 * {@link org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptor}) methods declared directly
 * on an entity are invoked when commands are routed to that entity, both for the entity itself and for commands routed
 * to a child entity added through {@link EntityMember}, and across a polymorphic hierarchy.
 * <p>
 * Each nested test uses a small, self-contained entity fixture rather than the shared domain fixtures used elsewhere in
 * this package, since these tests are only concerned with interceptor invocation, not the rest of the entity model's
 * behavior.
 *
 * @author Steven van Beelen
 */
class AnnotatedEntityMetamodelCommandInterceptorTest {

    @Nested
    class BeforeStyleInterceptor extends AbstractAnnotatedEntityMetamodelTest<InterceptedEntity> {

        @Override
        protected AnnotatedEntityMetamodel<InterceptedEntity> getMetamodel() {
            return AnnotatedEntityMetamodel.forConcreteType(InterceptedEntity.class,
                                                            parameterResolverFactory,
                                                            handlerDefinition,
                                                            messageTypeResolver,
                                                            messageConverter,
                                                            eventConverter);
        }

        @Test
        void interceptorRunsBeforeHandlerAndHandlerStillExecutes() {
            entityState = new InterceptedEntity();

            dispatchInstanceCommand(new Ping("hello"));

            assertThat(entityState.invocations).containsExactly("intercepted", "handled");
        }
    }

    @Nested
    class SurroundStyleInterceptor extends AbstractAnnotatedEntityMetamodelTest<ShortCircuitingEntity> {

        @Override
        protected AnnotatedEntityMetamodel<ShortCircuitingEntity> getMetamodel() {
            return AnnotatedEntityMetamodel.forConcreteType(ShortCircuitingEntity.class,
                                                            parameterResolverFactory,
                                                            handlerDefinition,
                                                            messageTypeResolver,
                                                            messageConverter,
                                                            eventConverter);
        }

        @Test
        void interceptorShortCircuitsAndHandlerIsNeverInvoked() {
            entityState = new ShortCircuitingEntity();

            Object result = dispatchInstanceCommand(new Ping("hello"));

            assertThat(result).isEqualTo("short-circuited");
            assertThat(entityState.invocations).isEmpty();
        }
    }

    @Nested
    class MixedAnnotationInterceptors extends AbstractAnnotatedEntityMetamodelTest<MixedEntity> {

        @Override
        protected AnnotatedEntityMetamodel<MixedEntity> getMetamodel() {
            return AnnotatedEntityMetamodel.forConcreteType(MixedEntity.class,
                                                            parameterResolverFactory,
                                                            handlerDefinition,
                                                            messageTypeResolver,
                                                            messageConverter,
                                                            eventConverter);
        }

        @Test
        void bothCommandHandlerInterceptorAndDirectMessageHandlerInterceptorAreInvokedBeforeTheHandler() {
            entityState = new MixedEntity();

            dispatchInstanceCommand(new Ping("hello"));

            assertThat(entityState.invocations).contains("via-command-handler-interceptor",
                                                         "via-message-handler-interceptor");
            assertThat(entityState.invocations.getLast()).isEqualTo("handled");
        }
    }

    @Nested
    class ParentAndChildInterceptorOrdering extends AbstractAnnotatedEntityMetamodelTest<ParentEntity> {

        private final List<String> invocations = new ArrayList<>();

        @Override
        protected AnnotatedEntityMetamodel<ParentEntity> getMetamodel() {
            return AnnotatedEntityMetamodel.forConcreteType(ParentEntity.class,
                                                            parameterResolverFactory,
                                                            handlerDefinition,
                                                            messageTypeResolver,
                                                            messageConverter,
                                                            eventConverter);
        }

        @Test
        void parentInterceptorRunsBeforeChildInterceptorForCommandRoutedToChild() {
            entityState = new ParentEntity(invocations);
            entityState.child = new ChildEntity(invocations);

            dispatchInstanceCommand(new PingChild("hello"));

            assertThat(invocations).containsExactly("parent", "child", "child-handled");
        }
    }

    @Nested
    class PolymorphicSupertypeInterceptor extends AbstractAnnotatedEntityMetamodelTest<Shape> {

        @Override
        protected AnnotatedEntityMetamodel<Shape> getMetamodel() {
            return AnnotatedEntityMetamodel.forPolymorphicType(
                    Shape.class,
                    Set.of(Circle.class, Square.class),
                    parameterResolverFactory,
                    handlerDefinition,
                    messageTypeResolver,
                    messageConverter,
                    eventConverter
            );
        }

        @Test
        void supertypeInterceptorFiresForSupertypeDeclaredCommand() {
            entityState = new Circle();

            dispatchInstanceCommand(new Rename("new-name"));

            assertThat(entityState.invocations).containsExactly("intercepted");
            assertThat(entityState.name).isEqualTo("new-name");
        }

        @Test
        void supertypeInterceptorAlsoFiresForConcreteTypeOnlyCommand() {
            entityState = new Circle();

            dispatchInstanceCommand(new SetRadius(5));

            assertThat(entityState.invocations).containsExactly("intercepted");
            assertThat(((Circle) entityState).radius).isEqualTo(5);
        }
    }

    /**
     * Reproduces the scenario from the original bug report: an entity rejects a command based on its own current state
     * through a {@link CommandHandlerInterceptor}. Before the fix, this interceptor was silently never invoked.
     */
    @Nested
    class RegressionRejectingBasedOnEntityState extends AbstractAnnotatedEntityMetamodelTest<GiftCard> {

        @Override
        protected AnnotatedEntityMetamodel<GiftCard> getMetamodel() {
            return AnnotatedEntityMetamodel.forConcreteType(GiftCard.class,
                                                            parameterResolverFactory,
                                                            handlerDefinition,
                                                            messageTypeResolver,
                                                            messageConverter,
                                                            eventConverter);
        }

        @Test
        void interceptorRejectsRedeemCommandWhenAlreadyRedeemed() {
            entityState = new GiftCard();

            dispatchInstanceCommand(new RedeemGiftCard("card-1"));
            assertThat(entityState.redeemed).isTrue();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> dispatchInstanceCommand(new RedeemGiftCard("card-1")))
                    .withMessage("Gift card already redeemed");
        }
    }

    @SuppressWarnings("unused")
    static class InterceptedEntity {

        List<String> invocations = new ArrayList<>();

        @CommandHandlerInterceptor
        public void audit(CommandMessage command) {
            invocations.add("intercepted");
        }

        @CommandHandler
        public void handle(Ping command) {
            invocations.add("handled");
        }
    }

    @SuppressWarnings("unused")
    static class ShortCircuitingEntity {

        List<String> invocations = new ArrayList<>();

        @CommandHandlerInterceptor
        public MessageStream<?> guard(CommandMessage command, MessageHandlerInterceptorChain<CommandMessage> chain) {
            return MessageStream.just(
                    new GenericCommandResultMessage(new MessageType(String.class), "short-circuited"));
        }

        @CommandHandler
        public void handle(Ping command) {
            invocations.add("handled");
        }
    }

    @SuppressWarnings("unused")
    static class MixedEntity {

        List<String> invocations = new ArrayList<>();

        @CommandHandlerInterceptor
        public void auditOne(CommandMessage command) {
            invocations.add("via-command-handler-interceptor");
        }

        @org.axonframework.messaging.core.interception.annotation.MessageHandlerInterceptor(messageType = CommandMessage.class)
        public void auditTwo(CommandMessage command) {
            invocations.add("via-message-handler-interceptor");
        }

        @CommandHandler
        public void handle(Ping command) {
            invocations.add("handled");
        }
    }

    @SuppressWarnings("unused")
    static class ParentEntity {

        final List<String> invocations;

        @EntityMember
        ChildEntity child;

        ParentEntity(List<String> invocations) {
            this.invocations = invocations;
        }

        @CommandHandlerInterceptor
        public void audit(CommandMessage command) {
            invocations.add("parent");
        }
    }

    @SuppressWarnings("unused")
    static class ChildEntity {

        final List<String> invocations;

        ChildEntity(List<String> invocations) {
            this.invocations = invocations;
        }

        @CommandHandlerInterceptor
        public void audit(CommandMessage command) {
            invocations.add("child");
        }

        @CommandHandler
        public void handle(PingChild command) {
            invocations.add("child-handled");
        }
    }

    @SuppressWarnings("unused")
    abstract static class Shape {

        List<String> invocations = new ArrayList<>();
        String name;

        @CommandHandlerInterceptor
        public void audit(CommandMessage command) {
            invocations.add("intercepted");
        }

        @CommandHandler
        public void handle(Rename command) {
            this.name = command.name();
        }
    }

    @SuppressWarnings("unused")
    static class Circle extends Shape {

        int radius;

        @CommandHandler
        public void handle(SetRadius command) {
            this.radius = command.radius();
        }
    }

    @SuppressWarnings("unused")
    static class Square extends Shape {

        int side;
    }

    @SuppressWarnings("unused")
    static class GiftCard {

        boolean redeemed = false;

        @CommandHandlerInterceptor
        public void rejectIfAlreadyRedeemed(CommandMessage command) {
            if (redeemed) {
                throw new IllegalStateException("Gift card already redeemed");
            }
        }

        @CommandHandler
        public void handle(RedeemGiftCard command) {
            this.redeemed = true;
        }
    }

    record Ping(String value) {

    }

    record PingChild(String value) {

    }

    record Rename(String name) {

    }

    record SetRadius(int radius) {

    }

    record RedeemGiftCard(String id) {

    }
}
