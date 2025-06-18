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
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateCustomer;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.commands.PersonCommand;
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
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.modelling.entity.ConcreteEntityMetamodel;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.configuration.Module;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;

import static java.lang.String.format;

/**
 * Runs the administration test suite using the builders of {@link EntityMetamodel} and related classes.
 */
public class ImmutableBuilderEntityModelAdministrationTest extends AbstractAdministrationTestSuite {

    EntityMetamodel<ImmutablePerson> buildEntityMetamodel(Configuration configuration) {
        MessageTypeResolver typeResolver = configuration.getComponent(MessageTypeResolver.class);

        // Task is the list-based child-metamodel of Employee
        EntityMetamodel<ImmutableTask> taskMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableTask.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableTask.class))
                .instanceCommandHandler(typeResolver.resolveOrThrow(CompleteTaskCommand.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle((CompleteTaskCommand) command.getPayload(), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // SalaryInformation is the singular child-metamodel of Employee
        EntityMetamodel<ImmutableSalaryInformation> salaryInformationMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableSalaryInformation.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableSalaryInformation.class))
                .instanceCommandHandler(typeResolver.resolveOrThrow(GiveRaise.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle((GiveRaise) command.getPayload(), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // Employee is a concrete entity type
        EntityMetamodel<ImmutableEmployee> employeeMetamodel = ConcreteEntityMetamodel
                .forEntityClass(ImmutableEmployee.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableEmployee.class))
                .creationalCommandHandler(typeResolver.resolveOrThrow(CreateEmployee.class).qualifiedName(),
                                          ((command, context) -> {
                                              EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                     configuration);
                                              ImmutableEmployee.handle((CreateEmployee) command.getPayload(),
                                                                       eventAppender);
                                              return MessageStream.empty().cast();
                                          }))
                .instanceCommandHandler(typeResolver.resolveOrThrow(AssignTaskCommand.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle((AssignTaskCommand) command.getPayload(), eventAppender);
                                            return MessageStream.empty().cast();
                                        }))
                .addChild(EntityChildMetamodel
                                  .list(ImmutableEmployee.class, taskMetamodel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forGetterEvolver(
                                          ImmutableEmployee::getTaskList, ImmutableEmployee::evolveTaskList
                                  ))
                                  .commandTargetResolver((candidates, commandMessage, ctx) -> {
                                      if (commandMessage.getPayload() instanceof CompleteTaskCommand completeTaskCommand) {
                                          return candidates.stream()
                                                           .filter(task -> task.getTaskId()
                                                                               .equals(completeTaskCommand.taskId()))
                                                           .findFirst()
                                                           .orElse(null);
                                      }
                                      return null;
                                  })
                                  .eventTargetMatcher((o, eventMessage, ctx) -> {
                                      if (eventMessage.getPayload() instanceof TaskCompleted taskAssigned) {
                                          return o.getTaskId().equals(taskAssigned.taskId());
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
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutableCustomer.class))
                .creationalCommandHandler(typeResolver.resolveOrThrow(CreateCustomer.class).qualifiedName(),
                                          ((command, context) -> {
                                              EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                     configuration);
                                              ImmutableCustomer.handle((CreateCustomer) command.getPayload(),
                                                                       eventAppender);
                                              return MessageStream.empty().cast();
                                          }))
                .build();

        return EntityMetamodel
                .forPolymorphicEntityType(ImmutablePerson.class)
                .addConcreteType(employeeMetamodel)
                .addConcreteType(customerMetamodel)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(ImmutablePerson.class))
                .instanceCommandHandler(typeResolver.resolveOrThrow(ChangeEmailAddress.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle((ChangeEmailAddress) command.getPayload(), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();
    }


    @Override
    Module getModule() {
        EventSourcedEntityModule<PersonIdentifier, ImmutablePerson> personEntityModule = EventSourcedEntityModule
                .declarative(PersonIdentifier.class, ImmutablePerson.class)
                .entityModel(this::buildEntityMetamodel)
                .entityFactory(c -> EventSourcedEntityFactory.fromEventMessage((identifier, eventMessage) -> {
                    if (eventMessage.getPayload() instanceof EmployeeCreated employeeCreated) {
                        return new ImmutableEmployee(employeeCreated);
                    }
                    if (eventMessage.getPayload() instanceof CustomerCreated customerCreated) {
                        return new ImmutableCustomer(customerCreated);
                    }
                    throw new IllegalArgumentException(
                            format("Unknown event type: %s", eventMessage.getPayloadType().getName()));
                }))
                .criteriaResolver(c -> (s, ctx) -> EventCriteria.havingTags("Person", s.key()))
                .entityIdResolver(config -> (message, context) -> {
                    if(message.getPayload() instanceof PersonCommand personCommand) {
                        return personCommand.identifier();
                    }
                    throw new IllegalArgumentException(
                            format("Unknown command type: %s", message.getPayloadType().getName()));
                });
        return StatefulCommandHandlingModule
                .named("ImmutableBuilderEntityModelAdministrationTest")
                .entities()
                .entity(personEntityModule)
                .commandHandlers()
                .build();
    }
}
