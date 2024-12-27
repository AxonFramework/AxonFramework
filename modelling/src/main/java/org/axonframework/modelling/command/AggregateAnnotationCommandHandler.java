/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.annotation.AnnotationCommandHandlerAdapter;
import org.axonframework.commandhandling.annotation.CommandMessageHandlingMember;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.Registration;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.modelling.command.inspection.CreationPolicyMember;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertThat;
import static org.axonframework.modelling.command.AggregateCreationPolicy.NEVER;

/**
 * Command handler that registers a set of {@link CommandHandler} based on annotations of an aggregate. Those
 * annotations may appear on methods, in which case a specific aggregate instance needs to be targeted by the command,
 * or on the constructor. The latter will create a new Aggregate instance, which is then stored in the repository.
 * <p>
 * Despite being an {@link CommandHandlingComponent} it does not actually handle the commands. During registration to
 * the {@link CommandBus} it registers the {@link CommandHandlingComponent}s directly instead of itself so duplicate
 * command handlers can be detected and handled correctly.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public class AggregateAnnotationCommandHandler<T> implements CommandHandlingComponent {

    private final Repository<T> repository;
    private final CommandTargetResolver commandTargetResolver;
    // TODO replace these MessageHandlers for MessageHandlingMembers, as the latter dictate the use of annotations
    private final List<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> handlers;
    private final Set<String> supportedCommandNames;
    private final Map<String, Set<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>>> supportedCommandsByName;
    private final Map<Class<? extends T>, CreationPolicyAggregateFactory<T>> factoryPerType;
    private final MessageNameResolver messageNameResolver;

    /**
     * Instantiate a Builder to be able to create a {@link AggregateAnnotationCommandHandler}.
     * <p>
     * The {@link CommandTargetResolver} is defaulted to a {@link AnnotationCommandTargetResolver}. The
     * {@link Repository} is a <b>hard requirement</b> and as such should be provided. Next to that, this Builder's goal
     * is to provide an {@link AggregateModel} (describing the structure of a given aggregate). To instantiate this
     * AggregateModel, either an {@link AggregateModel} can be provided directly or an {@code aggregateType} of type
     * {@link Class} can be used. The latter will internally resolve to an AggregateModel. Thus, either the
     * AggregateModel <b>or</b> the {@code aggregateType} should be provided.
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
     * Will assert that the {@link Repository} and {@link CommandTargetResolver} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if either of them is {@code null}. Next to that, the provided Builder's goal
     * is to create an {@link AggregateModel} (describing the structure of a given aggregate). To instantiate this
     * AggregateModel, either an {@link AggregateModel} can be provided directly or an {@code aggregateType} of type
     * {@link Class} can be used. The latter will internally resolve to an AggregateModel. Thus, either the
     * AggregateModel <b>or</b> the {@code aggregateType} should be provided. An AxonConfigurationException is thrown if
     * this criteria is not met.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AggregateAnnotationCommandHandler} instance
     */
    protected AggregateAnnotationCommandHandler(Builder<T> builder) {
        builder.validate();
        this.repository = builder.repository;
        this.commandTargetResolver = builder.commandTargetResolver;
        this.supportedCommandNames = new HashSet<>();
        this.supportedCommandsByName = new HashMap<>();
        this.messageNameResolver = builder.messageNameResolver;
        AggregateModel<T> aggregateModel = builder.buildAggregateModel();
        // Suppressing cast to Class<? extends T> as we are definitely dealing with implementations of T.
        //noinspection unchecked
        this.factoryPerType = initializeAggregateFactories(
                aggregateModel.types()
                              .map(type -> (Class<? extends T>) type)
                              .collect(Collectors.toList()),
                builder.creationPolicyAggregateFactory
        );

        this.handlers = initializeHandlers(aggregateModel);
    }

    private Map<Class<? extends T>, CreationPolicyAggregateFactory<T>> initializeAggregateFactories(
            List<Class<? extends T>> aggregateTypes,
            CreationPolicyAggregateFactory<T> configuredAggregateFactory
    ) {
        Map<Class<? extends T>, CreationPolicyAggregateFactory<T>> typeToFactory = new HashMap<>();
        for (Class<? extends T> aggregateType : aggregateTypes) {
            typeToFactory.put(aggregateType, configuredAggregateFactory != null
                    ? configuredAggregateFactory
                    : new NoArgumentConstructorCreationPolicyAggregateFactory<>(aggregateType)
            );
        }
        return typeToFactory;
    }

    /**
     * Subscribe this command handler to the given {@code commandBus}. The command handler will be subscribed for each
     * of the supported commands.
     *
     * @param commandBus The command bus instance to subscribe to
     * @return A handle that can be used to unsubscribe
     */
    public Registration subscribe(CommandBus commandBus) {
        List<Registration> subscriptions = supportedCommandsByName
                .entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().stream().map(
                        messageHandler -> commandBus.subscribe(entry.getKey(), messageHandler)
                ))
                .filter(Objects::nonNull)
                .toList();
        return () -> subscriptions.stream().map(Registration::cancel).reduce(Boolean::logicalOr).orElse(false);
    }

    /**
     * Initializes all the handlers. Handlers are deduplicated based on their signature. The signature includes the name
     * of the method and all parameter types. This is an effective override in the hierarchy.
     */
    private List<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> initializeHandlers(
            AggregateModel<T> aggregateModel) {
        List<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> handlersFound = new ArrayList<>();

        aggregateModel.allCommandHandlers()
                      .values()
                      .stream()
                      .flatMap(List::stream)
                      .collect(Collectors.groupingBy(this::getHandlerSignature))
                      .forEach((signature, commandHandlers) -> initializeHandler(
                              aggregateModel, commandHandlers.getFirst(), handlersFound
                      ));

        return handlersFound;
    }

    private String getHandlerSignature(MessageHandlingMember<? super T> handler) {
        return handler.unwrap(Executable.class)
                      .map(ReflectionUtils::toDiscernibleSignature)
                      .orElseThrow(() -> new IllegalStateException(
                              "A handler is missing an Executable. Please provide an "
                                      + "Executable in your MessageHandlingMembers"
                      ));
    }

    private void initializeHandler(AggregateModel<T> aggregateModel,
                                   MessageHandlingMember<? super T> handler,
                                   List<MessageHandler<CommandMessage<?>, CommandResultMessage<?>>> handlersFound) {

        handler.unwrap(CommandMessageHandlingMember.class).ifPresent(cmh -> {
            Optional<AggregateCreationPolicy> policy = handler.unwrap(CreationPolicyMember.class)
                                                              .map(CreationPolicyMember::creationPolicy);

            MessageHandler<CommandMessage<?>, CommandResultMessage<?>> messageHandler;
            if (cmh.isFactoryHandler()) {
                assertThat(
                        policy,
                        p -> p.map(AggregateCreationPolicy.ALWAYS::equals).orElse(true),
                        aggregateModel.type() + ": Static methods/constructors can only use creationPolicy ALWAYS"
                );
                messageHandler = new AggregateConstructorCommandHandler(handler);
            } else {
                messageHandler = switch (policy.orElse(NEVER)) {
                    case ALWAYS -> new AlwaysCreateAggregateCommandHandler(
                            handler, factoryPerType.get(handler.declaringClass())
                    );
                    case CREATE_IF_MISSING -> new AggregateCreateOrUpdateCommandHandler(
                            handler, factoryPerType.get(handler.declaringClass())
                    );
                    case NEVER -> new AggregateCommandHandler(handler);
                };
            }
            handlersFound.add(messageHandler);
            supportedCommandsByName.computeIfAbsent(cmh.commandName(), key -> new HashSet<>()).add(messageHandler);
            supportedCommandNames.add(cmh.commandName());
        });
    }

    @Override
    public Object handleSync(CommandMessage<?> commandMessage) throws Exception {
        return handlers.stream()
                       .filter(ch -> ch.canHandle(commandMessage))
                       .findFirst()
                       .orElseThrow(() -> new NoHandlerForCommandException(commandMessage))
                       .handleSync(commandMessage);
    }

    @Override
    public MessageStream<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                         ProcessingContext processingContext) {
        return handlers.stream()
                       .filter(ch -> ch.canHandle(message))
                       .findFirst()
                       .orElseThrow(() -> new NoHandlerForCommandException(message))
                       .handle(message, processingContext)
                       .mapMessage(m -> asCommandResultMessage(m, messageNameResolver::resolve));
    }

    @SuppressWarnings("unchecked")
    private static <R> CommandResultMessage<R> asCommandResultMessage(@Nullable Object commandResult, @Nonnull Function<Object, QualifiedName> nameResolver) {
        if (commandResult instanceof CommandResultMessage) {
            return (CommandResultMessage<R>) commandResult;
        } else if (commandResult instanceof Message) {
            Message<R> commandResultMessage = (Message<R>) commandResult;
            return new GenericCommandResultMessage<>(commandResultMessage);
        }
        QualifiedName name = commandResult == null
                ? QualifiedNameUtils.fromDottedName("empty.command.result")
                : nameResolver.apply(commandResult);
        return new GenericCommandResultMessage<>(name, (R) commandResult);
    }

    @Override
    public boolean canHandle(CommandMessage<?> message) {
        return handlers.stream()
                       .anyMatch(ch -> ch.canHandle(message));
    }

    /**
     * Resolves the value to return when the given {@code command} has created the given {@code aggregate}. This
     * implementation returns the identifier of the created aggregate.
     * <p>
     * This method may be overridden to change the return value of this Command Handler
     *
     * @param command          The command being executed
     * @param createdAggregate The aggregate that has been created as a result of the command
     * @return The value to report as result of the command
     */
    protected Object resolveReturnValue(@SuppressWarnings("unused") CommandMessage<?> command,
                                        Aggregate<T> createdAggregate) {
        return createdAggregate.identifier();
    }

    @Override
    public Set<String> supportedCommandNames() {
        return supportedCommandNames;
    }

    /**
     * Builder class to instantiate a {@link AggregateAnnotationCommandHandler}.
     * <p>
     * The {@link CommandTargetResolver} is defaulted to an {@link AnnotationCommandTargetResolver} The
     * {@link Repository} is a <b>hard requirement</b> and as such should be provided. Next to that, this Builder's goal
     * is to provide an {@link AggregateModel} (describing the structure of a given aggregate). To instantiate this
     * AggregateModel, either an AggregateModel can be provided directly or an {@code aggregateType} of type
     * {@link Class} can be used. The latter will internally resolve to an AggregateModel. Thus, either the
     * AggregateModel
     * <b>or</b> the {@code aggregateType} should be provided.
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
        private CreationPolicyAggregateFactory<T> creationPolicyAggregateFactory;
        private MessageNameResolver messageNameResolver = new ClassBasedMessageNameResolver();

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
         * Sets the {@link HandlerDefinition} used to create concrete handlers for the given {@code aggregateType}. Only
         * used if the {@code aggregateType} approach is selected to create an {@link AggregateModel}.
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
         * Sets the {@link CreationPolicyAggregateFactory<T>} for generic type {@code T}.
         * <p>
         * The aggregate factory must produce a new instance of the aggregate root based on the supplied identifier.
         * When dealing with a polymorphic aggregate, the given {@code creationPolicyAggregateFactory} will be used for
         * <b>every</b> {@link AggregateModel#types() type}.
         *
         * @param creationPolicyAggregateFactory The {@link CreationPolicyAggregateFactory} the constructs an aggregate
         *                                       instance based on an identifier.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder<T> creationPolicyAggregateFactory(
                CreationPolicyAggregateFactory<T> creationPolicyAggregateFactory
        ) {
            this.creationPolicyAggregateFactory = creationPolicyAggregateFactory;
            return this;
        }

        /**
         * Sets the {@link MessageNameResolver} to be used in order to resolve QualifiedName for dispatched Command messages.
         * If not set, a {@link ClassBasedMessageNameResolver} is used by default.
         *
         * @param messageNameResolver which provides QualifiedName for Event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageNameResolver(MessageNameResolver messageNameResolver) {
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            this.messageNameResolver = messageNameResolver;
            return this;
        }

        /**
         * Instantiate the {@link AggregateModel} of generic type {@code T} describing the structure of the Aggregate
         * this {@link AggregateAnnotationCommandHandler} will handle commands for.
         *
         * @return a {@link AggregateModel} of generic type {@code T} describing the Aggregate this
         * {@link AggregateAnnotationCommandHandler} will handle commands for
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

    private class AggregateConstructorCommandHandler
            implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandlingMember<?> handler;

        public AggregateConstructorCommandHandler(MessageHandlingMember<?> handler) {
            this.handler = handler;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object handleSync(CommandMessage<?> command) throws Exception {
            Aggregate<T> aggregate = repository.newInstance(() -> (T) handler.handleSync(command, null));
            return resolveReturnValue(command, aggregate);
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message, null);
        }
    }

    private class AlwaysCreateAggregateCommandHandler
            implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandlingMember<? super T> handler;
        private final CreationPolicyAggregateFactory<T> factoryMethod;

        private AlwaysCreateAggregateCommandHandler(MessageHandlingMember<? super T> handler,
                                                    CreationPolicyAggregateFactory<T> factoryMethod) {
            this.handler = handler;
            this.factoryMethod = factoryMethod;
        }

        @Override
        public Object handleSync(CommandMessage<?> command) throws Exception {
            return handleNewInstanceCreation(command, factoryMethod, handler, resolveNullableAggregateId(command));
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message, null);
        }
    }

    private class AggregateCreateOrUpdateCommandHandler
            implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandlingMember<? super T> handler;
        private final CreationPolicyAggregateFactory<T> factoryMethod;

        public AggregateCreateOrUpdateCommandHandler(MessageHandlingMember<? super T> handler,
                                                     CreationPolicyAggregateFactory<T> factoryMethod) {
            this.handler = handler;
            this.factoryMethod = factoryMethod;
        }

        @Override
        public Object handleSync(CommandMessage<?> command) throws Exception {
            VersionedAggregateIdentifier versionedAggregateIdentifier = resolveNullableAggregateId(command);

            Object result;
            if (versionedAggregateIdentifier != null) {
                Aggregate<T> instance = repository.loadOrCreate(
                        versionedAggregateIdentifier.getIdentifier(),
                        () -> factoryMethod.create(versionedAggregateIdentifier.getIdentifierValue())
                );
                result = instance.handle(command);
            } else {
                result = handleNewInstanceCreation(
                        command, factoryMethod, handler, resolveNullableAggregateId(command)
                );
            }
            return result;
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message, null);
        }
    }

    private VersionedAggregateIdentifier resolveNullableAggregateId(CommandMessage<?> command) {
        try {
            return commandTargetResolver.resolveTarget(command);
        } catch (IdentifierMissingException e) {
            // Couldn't find identifier in given command, so defaulting to null.
            // Assuming it will be set in the command handler.
            return null;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("It does not identify the target aggregate.")) {
                // Couldn't find identifier in given command, so defaulting to null.
                // Assuming it will be set in the command handler.
                return null;
            }
            throw e;
        }
    }

    private Object handleNewInstanceCreation(CommandMessage<?> command,
                                             CreationPolicyAggregateFactory<T> factoryMethod,
                                             MessageHandlingMember<? super T> handler,
                                             VersionedAggregateIdentifier commandMessageVersionedId) throws Exception {
        AtomicReference<Object> response = new AtomicReference<>();
        AtomicReference<Exception> exceptionDuringInit = new AtomicReference<>();
        Object commandMessageAggregateId = Optional.ofNullable(commandMessageVersionedId)
                                                   .map(VersionedAggregateIdentifier::getIdentifierValue)
                                                   .orElse(null);

        Aggregate<T> aggregate = repository.newInstance(
                () -> factoryMethod.create(commandMessageAggregateId),
                a -> {
                    try {
                        response.set(a.handle(command));
                    } catch (Exception e) {
                        exceptionDuringInit.set(e);
                    }
                }
        );

        if (exceptionDuringInit.get() != null) {
            throw exceptionDuringInit.get();
        }

        return handlerHasVoidReturnType(handler) ? resolveReturnValue(command, aggregate) : response.get();
    }

    private static <T> boolean handlerHasVoidReturnType(MessageHandlingMember<? super T> handler) {
        return handler.unwrap(Method.class)
                      .map(Method::getReturnType)
                      .filter(void.class::equals)
                      .isPresent();
    }

    private class AggregateCommandHandler implements MessageHandler<CommandMessage<?>, CommandResultMessage<?>> {

        private final MessageHandlingMember<? super T> handler;

        public AggregateCommandHandler(MessageHandlingMember<? super T> handler) {
            this.handler = handler;
        }

        @Override
        public Object handleSync(CommandMessage<?> command) throws Exception {
            VersionedAggregateIdentifier iv = commandTargetResolver.resolveTarget(command);
            return repository.load(iv.getIdentifier(), iv.getVersion()).handle(command);
        }

        @Override
        public boolean canHandle(CommandMessage<?> message) {
            return handler.canHandle(message, null);
        }
    }
}
