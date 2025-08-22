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

import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateCustomer;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.events.CustomerCreated;
import org.axonframework.integrationtests.testsuite.administration.events.EmployeeCreated;
import org.axonframework.integrationtests.testsuite.administration.events.TaskCompleted;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableCustomer;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableEmployee;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutablePerson;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableSalaryInformation;
import org.axonframework.integrationtests.testsuite.administration.state.immutable.ImmutableTask;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.modelling.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.entity.ConcreteEntityMetamodel;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.EntityMetamodelBuilder;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;
import org.axonframework.serialization.Converter;

import java.util.Objects;

import static java.lang.String.format;
import static org.axonframework.configuration.MessagingConfigurationDefaults.EVENT_CONVERTER_NAME;

/**
 * Runs the administration test suite using the builders of {@link EntityMetamodel} and related classes.
 */
public class ImmutableBuilderEntityModelAdministrationTest extends AbstractAdministrationTestSuite {

    EntityMetamodel<ImmutablePerson> buildEntityMetamodel(Configuration configuration,
                                                          EntityMetamodelBuilder<ImmutablePerson> builder) {
        MessageTypeResolver typeResolver = configuration.getComponent(MessageTypeResolver.class);
        Converter converter = configuration.getComponent(Converter.class, EVENT_CONVERTER_NAME);

        // Task is the list-based child-metamodel of Employee
        EntityMetamodel<ImmutableTask> taskMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableTask.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableTask.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(CompleteTaskCommand.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(command.payloadAs(CompleteTaskCommand.class, converter),
                                                          eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // SalaryInformation is the singular child-metamodel of Employee
        EntityMetamodel<ImmutableSalaryInformation> salaryInformationMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableSalaryInformation.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableSalaryInformation.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(GiveRaise.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(command.payloadAs(GiveRaise.class, converter), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // Employee is a concrete entity type
        EntityMetamodel<ImmutableEmployee> employeeMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableEmployee.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableEmployee.class, converter, typeResolver))
                .creationalCommandHandler(typeResolver.resolveOrThrow(CreateEmployee.class).qualifiedName(),
                                          ((command, context) -> {
                                              EventAppender eventAppender =
                                                      EventAppender.forContext(context, configuration);
                                              ImmutableEmployee.handle(
                                                      command.payloadAs(CreateEmployee.class, converter), eventAppender
                                              );
                                              return MessageStream.empty().cast();
                                          }))
                .instanceCommandHandler(typeResolver.resolveOrThrow(AssignTaskCommand.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(command.payloadAs(AssignTaskCommand.class, converter), eventAppender);
                                            return MessageStream.empty().cast();
                                        }))
                .addChild(EntityChildMetamodel
                                  .list(ImmutableEmployee.class, taskMetamodel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forGetterEvolver(
                                          ImmutableEmployee::getTaskList, ImmutableEmployee::evolveTaskList
                                  ))
                                  .commandTargetResolver((candidates, commandMessage, ctx) -> {
                                      if (commandMessage.type().name().equals(CompleteTaskCommand.class.getName())) {
                                          var completeTaskCommand =
                                                  commandMessage.payloadAs(CompleteTaskCommand.class, converter);
                                          return candidates.stream()
                                                           .filter(task -> task.getTaskId()
                                                                               .equals(completeTaskCommand.taskId()))
                                                           .findFirst()
                                                           .orElse(null);
                                      }
                                      return null;
                                  })
                                  .eventTargetMatcher((o, eventMessage, ctx) -> {
                                      if(eventMessage.type().name().equals(TaskCompleted.class.getName())) {
                                          TaskCompleted taskCompleted = converter.convert(eventMessage.payload(), TaskCompleted.class);
                                          Objects.requireNonNull(taskCompleted, "TaskCompleted event payload cannot be null");
                                          return o.getTaskId().equals(taskCompleted.taskId());
                                      }
                                      return false;
                                  })
                                  .build()

                )
                .addChild(EntityChildMetamodel
                                  .single(ImmutableEmployee.class, salaryInformationMetamodel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forGetterEvolver(
                                          ImmutableEmployee::salaryInformation,
                                          ImmutableEmployee::evolveSalaryInformation
                                  ))
                                  .build()
                )
                .build();

        // Customer is a concrete entity type
        EntityMetamodel<ImmutableCustomer> customerMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableCustomer.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableCustomer.class, converter, typeResolver))
                .creationalCommandHandler(typeResolver.resolveOrThrow(CreateCustomer.class).qualifiedName(),
                                          ((command, context) -> {
                                              EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                     configuration);
                                              ImmutableCustomer.handle(
                                                      command.payloadAs(CreateCustomer.class, converter), eventAppender
                                              );
                                              return MessageStream.empty().cast();
                                          }))
                .build();

        return EntityMetamodel
                .forPolymorphicEntityType(ImmutablePerson.class)
                .addConcreteType(employeeMetamodel)
                .addConcreteType(customerMetamodel)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutablePerson.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(ChangeEmailAddress.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(command.payloadAs(ChangeEmailAddress.class, converter),
                                                          eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        EventSourcedEntityModule<PersonIdentifier, ImmutablePerson> personEntityModule = EventSourcedEntityModule
                .declarative(PersonIdentifier.class, ImmutablePerson.class)
                .messagingModel(this::buildEntityMetamodel)
                .entityFactory(c -> EventSourcedEntityFactory.fromEventMessage((identifier, eventMessage) -> {
                    Converter converter = c.getComponent(Converter.class);
                    if(eventMessage.type().name().equals(EmployeeCreated.class.getName())) {
                        var employeeCreated = converter.convert(eventMessage.payload(), EmployeeCreated.class);
                        Objects.requireNonNull(employeeCreated, "EmployeeCreated event payload cannot be null");
                        return new ImmutableEmployee(employeeCreated);
                    }
                    if(eventMessage.type().name().equals(CustomerCreated.class.getName())) {
                        var customerCreated = converter.convert(eventMessage.payload(), CustomerCreated.class);
                        Objects.requireNonNull(customerCreated, "CustomerCreated event payload cannot be null");
                        return new ImmutableCustomer(customerCreated);
                    }
                    throw new IllegalArgumentException(
                            format("Unknown event type: %s", eventMessage.type()));
                }))
                .criteriaResolver(c -> (s, ctx) -> EventCriteria.havingTags("Person", s.key()))
                .entityIdResolver(PersonIdentifierEntityIdResolver::new);
        return configurer.componentRegistry(cr -> cr.registerModule(personEntityModule));
    }
}
