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

package org.axonframework.integrationtests.testsuite.administration;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.common.property.Property;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.annotation.AnnotatedEventHandlingComponent;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.SimpleEventSourcedComponent;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.common.PersonType;
import org.axonframework.integrationtests.testsuite.administration.state.Customer;
import org.axonframework.integrationtests.testsuite.administration.state.Employee;
import org.axonframework.integrationtests.testsuite.administration.state.Person;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.EntityModelBuilder;
import org.axonframework.modelling.entity.PolyMorphicEntityModelBuilder;
import org.axonframework.modelling.entity.PolymorphicEntityModel;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.ListEntityChildModel;
import org.axonframework.modelling.entity.child.SingleEntityChildModel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.resolveMemberGenericType;
import static org.axonframework.common.property.PropertyAccessStrategy.getProperty;

/**
 * THIS CLASS ONLY EXIST TO VERIFY THE CURRENT FUNCTIONALITY CAN BE APPLIED WITH ANNOTATIONS. THIS CLASS SHOULD NOT BE
 * REVIEWED. It's just proof of concept.
 * <p>
 * All needed classes/annotations are in this same class, so it can easily be skipped during review.
 */
public class AnnotationBasedAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        EntityModel<Person> personModel = new AnnotatedEventSourcedEntityModel<>(
                Person.class,
                configuration.getComponent(ParameterResolverFactory.class),
                Set.of(Customer.class, Employee.class));


        EventSourcingRepository<PersonIdentifier, Person> repository = new EventSourcingRepository<>(
                PersonIdentifier.class,
                Person.class,
                configuration.getComponent(EventStore.class),
                (type, id) -> {
                    if (id.type() == PersonType.EMPLOYEE) {
                        return new Employee();
                    } else if (id.type() == PersonType.CUSTOMER) {
                        return new Customer();
                    }
                    throw new IllegalArgumentException("Unknown type: " + id.type());
                },
                s -> EventCriteria.havingTags("Person", s.identifier()),
                personModel
        );

        return new EntityCommandHandlingComponent<>(
                repository,
                personModel,
                new AnnotationBasedEntityIdResolver<>()
        );
    }

    @Override
    void canGiveRaiseToEmployee() {
        // TODO This will not work, as ESH's now cannot return entities (or better said, it's lost)
    }

    private class AnnotatedEventSourcedEntityModel<E> implements EntityModel<E>, DescribableComponent {

        private final Class<E> entityType;
        private final EntityModel<E> entityModel;


        public AnnotatedEventSourcedEntityModel(Class<E> entityType,
                                                ParameterResolverFactory parameterResolverFactory,
                                                Set<Class<? extends E>> subTypes
        ) {
            this.entityType = entityType;
            Map<Class<? extends E>, EntityModel<? extends E>> collect =
                    subTypes.stream().collect(Collectors.toMap(
                            c -> c,
                            c -> new AnnotatedEventSourcedEntityModel<>(c, parameterResolverFactory, Set.of())));

            AnnotatedHandlerInspector<E> inspected = AnnotatedHandlerInspector.inspectType(entityType,
                                                                                           parameterResolverFactory);

            EntityModelBuilder<E> builder;
            if (collect.isEmpty()) {
                builder = EntityModel.forEntityType(entityType)
                                     .entityEvolver(createEntityEvolver(inspected));
            } else {
                PolyMorphicEntityModelBuilder<E> polymorphicBuilder = PolymorphicEntityModel
                        .forSuperType(entityType)
                        .entityEvolver(createEntityEvolver(inspected));
                collect.forEach((subType, model) -> {
                    polymorphicBuilder.addConcreteType(model);
                });
                builder = polymorphicBuilder;
            }


            initializeCommandHandlers(builder, inspected);
            initializeChildren(builder, parameterResolverFactory);

            this.entityModel = builder.build();
        }

        private EntityEvolver<E> createEntityEvolver(AnnotatedHandlerInspector<E> inspected) {
            Stream<MessageHandlingMember<? super E>> handlers = inspected.getHandlers(entityType);
            if (handlers == null) {
                return new SimpleEventSourcedComponent<>(Collections.emptyMap());
            }
            return new SimpleEventSourcedComponent<>(
                    handlers.collect(Collectors.toMap(
                            m -> new QualifiedName(m.payloadType()),
                            m -> (entity, event, context) -> {
                                var eventHandler = new AnnotatedEventHandlingComponent<>(entity, inspected);
                                var eventHandlerResult = eventHandler.handle(event, context)
                                                                     .asCompletableFuture()
                                                                     .join();
                                return entityFromStreamResultOrUpdatedExisting(eventHandlerResult, entity);
                            }
                    ))
            );
        }

        private E entityFromStreamResultOrUpdatedExisting(MessageStream.Entry<?> potentialEntityFromStream,
                                                          E existing) {
            if (potentialEntityFromStream != null) {
                var resultPayload = potentialEntityFromStream.message().getPayload();
                if (resultPayload != null && existing.getClass().isAssignableFrom(resultPayload.getClass())) {
                    //noinspection unchecked
                    return (E) existing.getClass().cast(resultPayload);
                }
            }
            return existing;
        }

        private void initializeCommandHandlers(EntityModelBuilder<E> builder,
                                               AnnotatedHandlerInspector<E> inspected) {
            inspected
                    .getHandlers(entityType)
                    .forEach(handler -> builder
                            .commandHandler(new QualifiedName(handler.payloadType()),
                                            ((command, entity, context) -> handler
                                                    .handle(command, context, entity)
                                                    .<CommandResultMessage<?>>mapMessage(GenericCommandResultMessage::new)
                                                    .first())));
        }

        private void initializeChildren(EntityModelBuilder<E> builder,
                                        ParameterResolverFactory parameterResolverFactory) {
            StreamSupport.stream(ReflectionUtils.fieldsOf(entityType).spliterator(), false)
                         .filter(field -> field.isAnnotationPresent(EntityMember.class))
                         .forEach(field -> {
                             if (field.getType() == List.class) {
                                 var childType = resolveMemberGenericType(field, 0).orElseThrow(
                                         () -> new AxonConfigurationException(format(
                                                 "Unable to resolve entity type of member [%s]. Please provide type explicitly in @AggregateMember annotation.",
                                                 ReflectionUtils.getMemberGenericString(field)
                                         )));
                                 var childModel = new AnnotatedEventSourcedEntityModel<>((Class<Object>) childType,
                                                                                         parameterResolverFactory,
                                                                                         Set.of());
                                 Map<String, Object> attributes = AnnotationUtils.findAnnotationAttributes(field,
                                                                                                           EntityMember.class)
                                                                                 .get();
                                 Class<MessageForwardingMode> forwardingModeclass = (Class<MessageForwardingMode>) attributes.get(
                                         "forwardingMode");
                                 MessageForwardingMode forwardingMode;
                                 try {
                                     forwardingMode = forwardingModeclass.getDeclaredConstructor().newInstance();
                                     forwardingMode.initialize(field, childModel);
                                 } catch (Exception e) {
                                     throw new AxonConfigurationException(format(
                                             "Unable to initialize event forwarding mode [%s] for field [%s]",
                                             forwardingModeclass.getName(),
                                             field
                                     ), e);
                                 }
                                 builder.addChild(
                                         ListEntityChildModel
                                                 .forEntityModel(entityType, childModel)
                                                 .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                                                         entityType, field.getName())
                                                 )
                                                 .commandTargetMatcher((o, commandMessage) ->
                                                                               forwardingMode.matches(commandMessage,
                                                                                                      o))
                                                 .eventTargetMatcher((o, eventMessage) ->
                                                                             forwardingMode.matches(eventMessage, o))
                                                 .build()
                                 );
                             } else {
                                 // Assume it's a single entity
                                 var childType = field.getType();
                                 var childModel = new AnnotatedEventSourcedEntityModel<>((Class<Object>) childType,
                                                                                         parameterResolverFactory,
                                                                                         Set.of());
                                 builder.addChild(
                                         SingleEntityChildModel
                                                 .forEntityModel(entityType, childModel)
                                                 .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                                                         entityType, field.getName())
                                                 )
                                                 .build()


                                 );
                             }
                         });
        }

        @Override
        public Set<QualifiedName> supportedCommands() {
            return entityModel.supportedCommands();
        }

        @Override
        public MessageStream.Single<CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                              E entity,
                                                                              ProcessingContext context
        ) {
            return entityModel.handle(message, entity, context);
        }

        @Override
        public void describeTo(@Nonnull ComponentDescriptor descriptor) {
            descriptor.describeWrapperOf(entityModel);
            descriptor.describeProperty("entityType", entityType());
        }

        @Override
        public E evolve(@Nonnull E entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
            return entityModel.evolve(entity, event, context);
        }

        @Override
        public Class<E> entityType() {
            return entityType;
        }
    }

    @Documented
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EntityMember {

        Class<? extends MessageForwardingMode> forwardingMode() default MatchingInstancesMessageForwardingMode.class;

        String routingKey() default "";
    }

    public static class MatchingInstancesMessageForwardingMode implements MessageForwardingMode {

        private static final String EMPTY_STRING = "";
        /**
         * Placeholder value for {@code null} properties, indicating that no property is available
         */
        private static final Property<Object> NO_PROPERTY = new MatchingInstancesMessageForwardingMode.NoProperty();

        private final Map<Class, Property> routingProperties = new ConcurrentHashMap<>();
        private final Map<Class, Property> entityRoutingKeyProperties = new ConcurrentHashMap<>();

        private String routingKey;
        private EntityModel childEntity;

        public MatchingInstancesMessageForwardingMode() {
        }

        @Override
        public void initialize(@javax.annotation.Nonnull Member member,
                               @javax.annotation.Nonnull EntityModel childEntity) {
            this.childEntity = childEntity;
            this.routingKey = AnnotationUtils.findAnnotationAttributes((AnnotatedElement) member,
                                                                       EntityMember.class)
                                             .map(map -> (String) map.get("routingKey"))
                                             .filter(key -> !Objects.equals(key, EMPTY_STRING))
                                             .orElse(getRoutingKeyProperty(childEntity.entityType()));
            routingProperties.clear();
        }

        @Override
        public <E> boolean matches(@javax.annotation.Nonnull Message<?> message,
                                   @javax.annotation.Nonnull E candidate) {
            Property routingProperty = routingProperties.computeIfAbsent(message.getPayloadType(),
                                                                         this::resolveProperty);
            if (routingProperty == null || routingProperty == NO_PROPERTY) {
                return false;
            }

            Object routingValue = routingProperty.getValue(message.getPayload());
            return matchesInstance(candidate, routingValue);
        }

        private Property<?> resolveProperty(Class<?> runtimeType) {
            Property<?> property = getProperty(runtimeType, routingKey);
            if (property == null) {
                return NO_PROPERTY;
            }
            return property;
        }

        @SuppressWarnings("unchecked")
        private <E> boolean matchesInstance(E candidate, Object routingValue) {
            Object identifier = entityRoutingKeyProperties.computeIfAbsent(
                    candidate.getClass(),
                    c -> getProperty(childEntity.entityType(), getRoutingKeyProperty(childEntity.entityType()))
            ).getValue(candidate);

            return Objects.equals(routingValue, identifier);
        }

        private static class NoProperty implements Property<Object> {

            @Override
            public <V> V getValue(Object target) {
                // this code should never be reached
                throw new UnsupportedOperationException("Property not found on target");
            }
        }

        private String getRoutingKeyProperty(Class<?> childEntityClass) {
            return Arrays.stream(childEntityClass.getDeclaredFields())
                         .filter(field -> field.isAnnotationPresent(EntityId.class))
                         .findFirst()
                         .map(Field::getName)
                         .orElseThrow(() -> new IllegalStateException(
                                 String.format("No field annotated with @%s found in %s",
                                               EntityId.class.getSimpleName(),
                                               childEntity.entityType().getSimpleName())));
        }
    }

    public interface MessageForwardingMode {

        default void initialize(@javax.annotation.Nonnull Member member,
                                @javax.annotation.Nonnull EntityModel childEntity) {
        }

        <E> boolean matches(@javax.annotation.Nonnull Message<?> message, @javax.annotation.Nonnull E candidate);
    }
}
