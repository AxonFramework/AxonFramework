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
import org.axonframework.configuration.Module;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateCustomer;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.commands.PersonCommand;
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
import org.axonframework.modelling.AnnotationBasedEntityEvolvingComponent;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.axonframework.modelling.entity.ConcreteEntityMetamodel;
import org.axonframework.modelling.entity.EntityMetamodel;
import org.axonframework.modelling.entity.EntityMetamodelBuilder;
import org.axonframework.modelling.entity.child.ChildEntityFieldDefinition;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;
import org.axonframework.serialization.Converter;

import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

/**
 * Runs the administration test suite using the builders of {@link EntityMetamodel} and related classes.
 */
public class MutableBuilderEntityModelAdministrationTest extends AbstractAdministrationTestSuite {


    EntityMetamodel<MutablePerson> buildEntityMetamodel(Configuration configuration,
                                                        EntityMetamodelBuilder<MutablePerson> builder) {
        MessageTypeResolver typeResolver = configuration.getComponent(MessageTypeResolver.class);
        Converter converter = configuration.getComponent(Converter.class);

        // Task is the list-based child-model of Employee
        EntityMetamodel<MutableTask> taskMetamodel = ConcreteEntityMetamodel
                .forEntityClass(MutableTask.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(MutableTask.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(CompleteTaskCommand.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(converter.convert(command.getPayload(), CompleteTaskCommand.class), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // SalaryInformation is the singular child-model of Employee
        EntityMetamodel<MutableSalaryInformation> salaryInformationMetamodel = ConcreteEntityMetamodel
                .forEntityClass(MutableSalaryInformation.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(MutableSalaryInformation.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(GiveRaise.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                            entity.handle(converter.convert(command.getPayload(), GiveRaise.class), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();

        // Employee is a concrete entity type
        EntityMetamodel<MutableEmployee> employeeMetamodel = ConcreteEntityMetamodel
                .forEntityClass(MutableEmployee.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(MutableEmployee.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(CreateEmployee.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(converter.convert(command.getPayload(), CreateEmployee.class), eventAppender);
                                            return MessageStream.empty().cast();
                                        }))
                .instanceCommandHandler(typeResolver.resolveOrThrow(AssignTaskCommand.class).qualifiedName(),
                                        ((command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(converter.convert(command.getPayload(), AssignTaskCommand.class), eventAppender);
                                            return MessageStream.empty().cast();
                                        }))
                .addChild(EntityChildMetamodel
                                  .list(MutableEmployee.class, taskMetamodel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forGetterSetter(
                                          MutableEmployee::getTaskList, MutableEmployee::setTaskList
                                  ))
                                  .commandTargetResolver((candidates, commandMessage, ctx) -> {
                                      if(commandMessage.type().name().equals(CompleteTaskCommand.class.getName())) {
                                          CompleteTaskCommand assignTaskCommand = converter.convert(commandMessage.getPayload(), CompleteTaskCommand.class);
                                          Objects.requireNonNull(assignTaskCommand, "AssignTaskCommand payload cannot be null");
                                          return candidates.stream()
                                                           .filter(task -> task.getTaskId()
                                                                               .equals(assignTaskCommand.taskId()))
                                                           .findFirst()
                                                           .orElse(null);
                                      }
                                      return null;
                                  })
                                  .eventTargetMatcher((o, eventMessage, ctx) -> {
                                     if(eventMessage.type().name().equals(TaskCompleted.class.getName())) {
                                         TaskCompleted taskCompleted = converter.convert(eventMessage.getPayload(), TaskCompleted.class);
                                         Objects.requireNonNull(taskCompleted, "TaskCompleted event payload cannot be null");
                                         return o.getTaskId().equals(taskCompleted.taskId());
                                      }
                                      return false;
                                  })
                                  .build()

                )
                .addChild(EntityChildMetamodel
                                  .single(MutableEmployee.class, salaryInformationMetamodel)
                                  .childEntityFieldDefinition(ChildEntityFieldDefinition.forFieldName(
                                          MutableEmployee.class, "salary"
                                  ))
                                  .build()
                )
                .build();

        // Customer is a concrete entity type
        EntityMetamodel<MutableCustomer> customerMetamodel = ConcreteEntityMetamodel
                .forEntityClass(MutableCustomer.class)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(MutableCustomer.class, converter, typeResolver))
                .instanceCommandHandler(
                        typeResolver.resolveOrThrow(CreateCustomer.class).qualifiedName(),
                        ((command, entity, context) -> {
                            EventAppender eventAppender = EventAppender.forContext(context, configuration);
                            entity.handle(converter.convert(command.getPayload(), CreateCustomer.class), eventAppender);
                            return MessageStream.empty().cast();
                        }))
                .build();

        // Person is the polymorphic entity type
        return EntityMetamodel
                .forPolymorphicEntityType(MutablePerson.class)
                .addConcreteType(employeeMetamodel)
                .addConcreteType(customerMetamodel)
                .entityEvolver(new AnnotationBasedEntityEvolvingComponent<>(MutablePerson.class, converter, typeResolver))
                .instanceCommandHandler(typeResolver.resolveOrThrow(ChangeEmailAddress.class).qualifiedName(),
                                        (command, entity, context) -> {
                                            EventAppender eventAppender = EventAppender.forContext(context,
                                                                                                   configuration);
                                            entity.handle(converter.convert(command.getPayload(), ChangeEmailAddress.class), eventAppender);
                                            return MessageStream.empty().cast();
                                        })
                .build();
    }

    @Override
    Module getModule() {
        EventSourcedEntityModule<PersonIdentifier, MutablePerson> personEntityModule = EventSourcedEntityModule
                .declarative(PersonIdentifier.class, MutablePerson.class)
                .messagingModel(this::buildEntityMetamodel)
                .entityFactory(c -> EventSourcedEntityFactory.fromIdentifier(id -> {
                    if (id.type() == PersonType.EMPLOYEE) {
                        return new MutableEmployee();
                    } else if (id.type() == PersonType.CUSTOMER) {
                        return new MutableCustomer();
                    }
                    throw new IllegalArgumentException("Unknown type: " + id.type());
                }))
                .criteriaResolver(c -> (s, ctx) -> EventCriteria.havingTags("Person", s.key()))
                .entityIdResolver(config -> (message, context) -> {
                    List<Class<? extends PersonCommand>> personCommandTypes = List.of(
                            AssignTaskCommand.class,
                            CreateCustomer.class,
                            CreateEmployee.class,
                            ChangeEmailAddress.class,
                            AssignTaskCommand.class,
                            CompleteTaskCommand.class,
                            GiveRaise.class
                    );
                    var clazz = personCommandTypes.stream().filter(type -> type.getName().equals(message.type().name()))
                                                  .findFirst()
                                                  .orElseThrow(() -> new IllegalArgumentException(format(
                                                          "Unknown command type: %s",
                                                          message.type().name())));
                    PersonCommand personCommand = config.getComponent(Converter.class)
                                                        .convert(message.getPayload(), clazz);
                    return Objects.requireNonNull(personCommand).identifier();
                });
        return StatefulCommandHandlingModule
                .named("MutableBuilderEntityModelAdministrationTest")
                .entities()
                .entity(personEntityModule)
                .commandHandlers()
                .build();
    }
}
