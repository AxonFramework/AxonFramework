/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandMessageHandler;
import org.axonframework.commandhandling.CommandMessageHandlingMember;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.CreationPolicyMember;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.modelling.command.AggregateCreationPolicy.NEVER;

/**
 * Command handler that handles commands based on {@link CommandHandler} annotations on an aggregate. Those annotations
 * may appear on methods, in which case a specific aggregate instance needs to be targeted by the command, or on the
 * constructor. The latter will create a new Aggregate instance, which is then stored in the repository.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateAnnotationCommandHandler<T> implements CommandMessageHandler {

    private final Repository<T> repository;
    private final CommandTargetResolver commandTargetResolver;
    private final List<MessageHandler<CommandMessage<?>>> handlers;
    private final Set<String> supportedCommandNames;

    /**
     * Instantiate a Builder to be able to create a {@link AggregateAnnotationCommandHandler}.
     * <p>
     * The {@link CommandTargetResolver} is defaulted to amn {@link AnnotationCommandTargetResolver}
     * The {@link Repository} is a <b>hard requirement</b> and as such should be provided.
     * Next to that, this Builder's goal is to provide an {@link AggregateModel} (describing the structure of a given
     * aggregate). To instantiate this AggregateModel, either an {@link AggregateModel} can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     *
     * @param <T> the type of aggregate this {@link AggregateAnnotationCommandHandler} handles commands for
     * @return a Builder to be able to create a {@link AggregateAnnotationCommandHandler}
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Instantiate a {@link AggregateAnnotationCommandHandler} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link Repository} and {@link CommandTargetResolver} are not {@code null}, and will throw
     * an {@link AxonConfigurationException} if either of them is {@code null}. Next to that, the provided Builder's
     * goal is to create an {@link AggregateModel} (describing the structure of a given aggregate). To instantiate this
     * AggregateModel, either an {@link AggregateModel} can be provided directly or an {@code aggregateType} of type
     * {@link Class} can be used. The latter will internally resolve to an AggregateModel. Thus, either the
     * AggregateModel <b>or</b> the {@code aggregateType} should be provided. An AxonConfigurationException is thrown
     * if this criteria is not met.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AggregateAnnotationCommandHandler} instance
     */
    protected AggregateAnnotationCommandHandler(Builder<T> builder) {
        builder.validate();
        this.repository = builder.repository;
        this.commandTargetResolver = builder.commandTargetResolver;
        this.supportedCommandNames = new HashSet<>();
        this.handlers = initializeHandlers(builder.buildAggregateModel());
    }

    /**
     * Subscribe this command handler to the given {@code commandBus}. The command handler will be subscribed
     * for each of the supported commands.
     *
     * @param commandBus The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
        List<Registration> subscriptions = supportedCommandNames()
                .stream()
                .map(supportedCommand -> commandBus.subscribe(supportedCommand, this))
                .filter(Objects::nonNull).collect(Collectors.toList());
        return () -> subscriptions.stream().map(Registration::cancel).reduce(Boolean::logicalOr).orElse(false);
    }

    private List<MessageHandler<CommandMessage<?>>> initializeHandlers(AggregateModel<T> aggregateModel) {
        List<MessageHandler<CommandMessage<?>>> handlersFound = new ArrayList<>();
        aggregateModel.allCommandHandlers()
                      .values()
                      .stream()
                      .flatMap(List::stream)
                      .forEach(handler -> initializeHandler(aggregateModel, handler, handlersFound));
        return handlersFound;
    }

    private void initializeHandler(AggregateModel<T> aggregateModel,
                                   MessageHandlingMember<? super T> handler,
                                   List<MessageHandler<CommandMessage<?>>> handlersFound) {


        handler.unwrap(CommandMessageHandlingMember.class).ifPresent(cmh -> {
            Optional<AggregateCreationPolicy> policy = handler.unwrap(CreationPolicyMember.class)
                                                              .map(CreationPolicyMember::creationPolicy);

            if (cmh.isFactoryHandler()) {
                assertThat(
                        policy,
                        p -> p.map(AggregateCreationPolicy.ALWAYS::equals).orElse(true),
                        aggregateModel.type() + ": Static methods/constructors can only use creationPolicy ALWAYS"
                );
                handlersFound.add(new AggregateConstructorCommandHandler(handler));
            } else {
                switch (policy.orElse(NEVER)) {
                    case ALWAYS:
                        handlersFound.add(new AlwaysCreateAggregateCommandHandler(
                                handler, aggregateModel.entityClass()::newInstance
                        ));
                        break;
                    case CREATE_IF_MISSING:
                        handlersFound.add(new AggregateCreateOrUpdateCommandHandler(
                                handler, aggregateModel.entityClass()::newInstance
                        ));
                        break;
                    case NEVER:
                        handlersFound.add(new AggregateCommandHandler(handler));
                        break;
                }
            }
            supportedCommandNames.add(cmh.commandName());
        });
    }

    @Override
    public Object handle(CommandMessage<?> commandMessage) throws Exception {
        return handlers.stream().filter(eh -> eh.canHandle(commandMessage))
                       .findFirst()
                       .orElseThrow(() -> new NoHandlerForCommandException(commandMessage))
                       .handle(commandMessage);
    }

    @Override
    public boolean canHandle(CommandMessage<?> message) {
        return handlers.stream().anyMatch(ch -> ch.canHandle(message));
    }

    /**
     * Resolves the value to return when the given {@code command} has created the given {@code aggregate}.
     * This implementation returns the identifier of the created aggregate.
     * <p>
     * This method may be overridden to change the return value of this Command Handler
     *
     * @param command          The command being executed
     * @param createdAggregate The aggregate that has been created as a result of the command
     * @return The value to report as result of the command
     */
    protected Object resolveReturnValue(CommandMessage<?> command, Aggregate<T> createdAggregate) {
        return createdAggregate.identifier();
    }

    @Override
    public Set<String> supportedCommandNames() {
        return supportedCommandNames;
    }

    /**
     * Builder class to instantiate a {@link AggregateAnnotationCommandHandler}.
     * <p>
     * The {@link CommandTargetResolver} is defaulted to an {@link AnnotationCommandTargetResolver}
     * The {@link Repository} is a <b>hard requirement</b> and as such should be provided.
     * Next to that, this Builder's goal is to provide an {@link AggregateModel} (describing the structure of a given
     * aggregate). To instantiate this AggregateModel, either an AggregateModel can be provided directly or an
     * {@code aggregateType} of type {@link Class} can be used. The latter will internally resolve to an
     * AggregateModel. Thus, either the AggregateModel <b>or</b> the {@code aggregateType} should be provided.
     *
     * @param <T> the type of aggregate this {@link AggregateAnnotationCommandHandler} handles commands for
     */
    public static class Builder<T> {

        private Repository<T> repository;
        private CommandTargetResolver commandTargetResolver = AnnotationCommandTargetResolver.builder().build();
        private Class<T> aggregateType;
        private ParameterResolverFactory parameterResolverFactory;
        private HandlerDefinition handlerDefinition;
        private AggregateModel<T> aggregateModel;

        /**
         * Sets the {@link Repository} used to add and load Aggregate instances of generic type {@code T} upon handling
         * commands for it.
         *
         * @param repository a {@link Repository} used to add and load Aggregate instances of generic type {@code T}
         *                   upon handling commands for it
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> repository(Repository<T> repository) {
            assertNonNull(repository, "Repository may not be null");
            this.repository = repository;
            return this;
        }

        /**
         * Sets the {@link CommandTargetResolver} used to resolve the command handling target. Defaults to an
         * {@link AnnotationCommandTargetResolver}.
         *
         * @param commandTargetResolver a {@link CommandTargetResolver} used to resolve the command handling target
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> commandTargetResolver(CommandTargetResolver commandTargetResolver) {
            assertNonNull(commandTargetResolver, "CommandTargetResolver may not be null");
            this.commandTargetResolver = commandTargetResolver;
            return this;
        }

        /**
         * Sets the {@code aggregateType} as a {@code Class}, specifying the type of aggregate an {@link AggregateModel}
         * should be created for. Either this field or the {@link #aggregateModel(AggregateModel)} should be provided to
         * correctly instantiate an {@link AggregateAnnotationCommandHandler}.
         *
         * @param aggregateType the {@code aggregateType} specifying the type of aggregate an {@link AggregateModel}
         *                      should be instantiated for
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> aggregateType(Class<T> aggregateType) {
            assertNonNull(aggregateType, "The aggregateType may not be null");
            this.aggregateType = aggregateType;
            return this;
        }

        /**
         * Sets the {@link ParameterResolverFactory} used to resolve parameters for annotated handlers contained in the
         * Aggregate. Only used if the {@code aggregateType} approach is selected to create an {@link AggregateModel}.
         *
         * @param parameterResolverFactory a {@link ParameterResolverFactory} used to resolve parameters for annotated
         *                                 handlers contained in the Aggregate
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> parameterResolverFactory(ParameterResolverFactory parameterResolverFactory) {
            assertNonNull(parameterResolverFactory, "ParameterResolverFactory may not be null");
            this.parameterResolverFactory = parameterResolverFactory;
            return this;
        }

        /**
         * Sets the {@link HandlerDefinition} used to create concrete handlers for the given {@code aggregateType}.
         * Only used if the {@code aggregateType} approach is selected to create an {@link AggregateModel}.
         *
         * @param handlerDefinition a {@link HandlerDefinition} used to create concrete handlers for the given
         *                          {@code aggregateType}
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> handlerDefinition(HandlerDefinition handlerDefinition) {
            assertNonNull(handlerDefinition, "HandlerDefinition may not be null");
            this.handlerDefinition = handlerDefinition;
            return this;
        }

        /**
         * Sets the {@link AggregateModel} of generic type {@code T}, describing the structure of the aggregate the
         * {@link AnnotationCommandHandlerAdapter} will handle. Either this field or the {@link #aggregateType(Class)}
         * should be provided to correctly instantiate an {@link AggregateAnnotationCommandHandler}.
         *
         * @param aggregateModel the {@link AggregateModel} of generic type {@code T} of the aggregate this
         *                       {@link Repository} will store
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> aggregateModel(AggregateModel<T> aggregateModel) {
            assertNonNull(aggregateModel, "AggregateModel may not be null");
            this.aggregateModel = aggregateModel;
            return this;
        }

        /**
         * Instantiate the {@link AggregateModel} of generic type {@code T} describing the structure of the Aggregate
         * this {@link AggregateAnnotationCommandHandler} will handle commands for.
         *
         * @return a {@link AggregateModel} of generic type {@code T} describing the Aggregate this {@link
         * AggregateAnnotationCommandHandler} will handle commands for
         */
        private AggregateModel<T> buildAggregateModel() {
            if (aggregateModel == null) {
                return inspectAggregateModel();
            } else {
                return aggregateModel;
            }
        }

        private AggregateModel<T> inspectAggregateModel() {
            if (parameterResolverFactory == null) {
                parameterResolverFactory = ClasspathParameterResolverFactory.forClass(aggregateType);
            }

            return handlerDefinition == null
                    ? AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType, parameterResolverFactory)
                    : AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType,
                                                                          parameterResolverFactory,
                                                                          handlerDefinition);
        }

        /**
         * Initializes a {@link AggregateAnnotationCommandHandler} as specified through this Builder.
         *
         * @return a {@link AggregateAnnotationCommandHandler} as specified through this Builder
         */
        public AggregateAnnotationCommandHandler<T> build() {
            return new AggregateAnnotationCommandHandler<>(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(repository, "The Repository is a hard requirement and should be provided");
            if (aggregateModel == null) {
                assertNonNull(
                        aggregateType,
                        "No AggregateModel is set, whilst either it or the aggregateType is a hard requirement"
                );
                return;
            }
            assertNonNull(
                    aggregateModel,
                    "No aggregateType is set, whilst either it or the AggregateModel is a hard requirement"
            );
        }
    }

    private class AggregateConstructorCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final MessageHandlingMember<?> handler;

        public AggregateConstructorCommandHandler(MessageHandlingMember<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
            Aggregate<T> aggregate = repository.newInstance(() -> (T) handler.handle(command, null));
            return resolveReturnValue(command, aggregate);
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message);
        }
    }

    private class AlwaysCreateAggregateCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final MessageHandlingMember<? super T> handler;
        private final Callable<T> factoryMethod;

        private AlwaysCreateAggregateCommandHandler(MessageHandlingMember<? super T> handler,
                                                    Callable<T> factoryMethod) {
            this.handler = handler;
            this.factoryMethod = factoryMethod;
        }

        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
            AtomicReference<Object> resultReference = new AtomicReference<>();
            Aggregate<T> aggregate = repository.newInstance(() -> {
                T newInstance = factoryMethod.call();
                resultReference.set(handler.handle(command, newInstance));
                return newInstance;
            });
            return handlerHasVoidReturnType() ? resolveReturnValue(command, aggregate) : resultReference.get();
        }

        public boolean handlerHasVoidReturnType() {
            return handler.unwrap(Method.class)
                          .map(Method::getReturnType)
                          .filter(void.class::equals)
                          .isPresent();
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message);
        }
    }

    private class AggregateCreateOrUpdateCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final MessageHandlingMember<? super T> handler;
        private final Callable<T> factoryMethod;

        public AggregateCreateOrUpdateCommandHandler(MessageHandlingMember<? super T> handler,
                                                     Callable<T> factoryMethod) {
            this.handler = handler;
            this.factoryMethod = factoryMethod;
        }

        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
            VersionedAggregateIdentifier commandMessageVersionedId = commandTargetResolver.resolveTarget(command);
            String commandMessageAggregateId = commandMessageVersionedId.getIdentifier();

            Aggregate<T> instance = repository.loadOrCreate(commandMessageAggregateId, factoryMethod);
            Object commandResult = instance.handle(command);
            Object aggregateId = instance.identifier();

            assertThat(
                    aggregateId,
                    id -> id != null && id.toString() != null && id.toString().equals(commandMessageAggregateId),
                    "Identifier must be set after handling the message"
            );
            return commandResult;
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message);
        }
    }

    private class AggregateCommandHandler implements MessageHandler<CommandMessage<?>> {

        private final MessageHandlingMember<? super T> handler;

        public AggregateCommandHandler(MessageHandlingMember<? super T> handler) {
            this.handler = handler;
        }

        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
            VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
            return repository.load(iv.getIdentifier(), iv.getVersion()).handle(command);
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message);
        }
    }
}
