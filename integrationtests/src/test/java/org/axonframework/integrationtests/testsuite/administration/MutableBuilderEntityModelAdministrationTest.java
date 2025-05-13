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

import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.AnnotationBasedEventSourcedComponent;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateCustomer;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.common.PersonType;
import org.axonframework.integrationtests.testsuite.administration.events.TaskCompleted;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableCustomer;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableEmployee;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutablePerson;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableSalaryInformation;
import org.axonframework.integrationtests.testsuite.administration.state.mutable.MutableTask;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.PolymorphicEntityModel;
import org.axonframework.modelling.entity.SimpleEntityModel;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.EntityChildModel;

/**
 * Runs the administration test suite using the builders of {@link SimpleEntityModel} and related classes.
 */
public class MutableBuilderEntityModelAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        MessageTypeResolver typeResolver = configuration.getComponent(MessageTypeResolver.class);

        // Task is the list-based child-model of Employee
        EntityModel<MutableTask> taskModel = SimpleEntityModel
                .forEntityClass(MutableTask.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(MutableTask.class))
                .instanceCommandHandler(typeResolver.resolve(CompleteTaskCommand.class).qualifiedName(),
                                        (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((CompleteTaskCommand) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();

        // SalaryInformation is the singular child-model of Employee
        EntityModel<MutableSalaryInformation> salaryInformationModel = SimpleEntityModel
                .forEntityClass(MutableSalaryInformation.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(MutableSalaryInformation.class))
                .instanceCommandHandler(typeResolver.resolve(GiveRaise.class).qualifiedName(),
                                        (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context,
                                                                                           configuration);
                                    entity.handle((GiveRaise) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();

        // Employee is a concrete entity type
        EntityModel<MutableEmployee> employeeModel = SimpleEntityModel
                .forEntityClass(MutableEmployee.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(MutableEmployee.class))
                .instanceCommandHandler(typeResolver.resolve(CreateEmployee.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((CreateEmployee) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                }))
                .instanceCommandHandler(typeResolver.resolve(AssignTaskCommand.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((AssignTaskCommand) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                }))
                .addChild(EntityChildModel
                                  .list(MutableEmployee.class, taskModel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forGetterSetter(
                                          MutableEmployee::getTaskList, MutableEmployee::setTaskList
                                  ))
                                  .commandTargetMatcher((task, commandMessage) -> {
                                      if (commandMessage.getPayload() instanceof CompleteTaskCommand completeTaskCommand) {
                                          return task.getTaskId()
                                                     .equals(completeTaskCommand.taskId());
                                      }
                                      return false;
                                  })
                                  .eventTargetMatcher((o, eventMessage) -> {
                                      if (eventMessage.getPayload() instanceof TaskCompleted taskAssigned) {
                                          return o.getTaskId().equals(taskAssigned.taskId());
                                      }
                                      return false;
                                  })
                                  .build()

                )
                .addChild(EntityChildModel
                                  .single(MutableEmployee.class, salaryInformationModel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                                          MutableEmployee.class, "salary"
                                  ))
                                  .build()
                )
                .build();

        // Customer is a concrete entity type
        EntityModel<MutableCustomer> customerModel = SimpleEntityModel
                .forEntityClass(MutableCustomer.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(MutableCustomer.class))
                .instanceCommandHandler(
                        typeResolver.resolve(CreateCustomer.class).qualifiedName(),
                        ((command, entity, context) -> {
                            EventAppender eventAppender = EventAppender.forContext(context, configuration);
                            entity.handle((CreateCustomer) command.getPayload(), eventAppender);
                            return MessageStream.empty().cast();
                        }))
                .build();

        // Person is the polymorphic entity type
        EntityModel<MutablePerson> personModel = PolymorphicEntityModel
                .forSuperType(MutablePerson.class)
                .addConcreteType(employeeModel)
                .addConcreteType(customerModel)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(MutablePerson.class))
                .instanceCommandHandler(typeResolver.resolve(ChangeEmailAddress.class).qualifiedName(),
                                        (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((ChangeEmailAddress) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();

        EventSourcingRepository<PersonIdentifier, MutablePerson> repository = new EventSourcingRepository<>(
                PersonIdentifier.class,
                MutablePerson.class,
                configuration.getComponent(EventStore.class),
                EventSourcedEntityFactory.fromIdentifier(id -> {
                    if (id.type() == PersonType.EMPLOYEE) {
                        return new MutableEmployee();
                    } else if (id.type() == PersonType.CUSTOMER) {
                        return new MutableCustomer();
                    }
                    throw new IllegalArgumentException("Unknown type: " + id.type());
                }),
                s -> EventCriteria.havingTags("Person", s.key()),
                personModel
        );

        return new EntityCommandHandlingComponent<>(
                repository,
                personModel,
                new AnnotationBasedEntityIdResolver<>()
        );
    }
}
