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
import org.axonframework.integrationtests.testsuite.administration.events.RaiseGiven;
import org.axonframework.integrationtests.testsuite.administration.events.TaskCompleted;
import org.axonframework.integrationtests.testsuite.administration.state.Customer;
import org.axonframework.integrationtests.testsuite.administration.state.Employee;
import org.axonframework.integrationtests.testsuite.administration.state.Person;
import org.axonframework.integrationtests.testsuite.administration.state.SalaryInformation;
import org.axonframework.integrationtests.testsuite.administration.state.Task;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.modelling.annotation.AnnotationBasedEntityIdResolver;
import org.axonframework.modelling.entity.EntityCommandHandlingComponent;
import org.axonframework.modelling.entity.EvolvableEntityModel;
import org.axonframework.modelling.entity.child.ListEvolvableEntityChildModel;
import org.axonframework.modelling.entity.PolymorphicEvolvableEntityModel;
import org.axonframework.modelling.entity.SimpleEvolvableEntityModel;
import org.axonframework.modelling.entity.child.SingleEvolvableEntityChildModel;

public class LambdaBasedAdministrationTest extends AbstractAdministrationTestSuite {

    @Override
    CommandHandlingComponent getCommandHandlingComponent(Configuration configuration) {
        MessageTypeResolver typeResolver = configuration.getComponent(MessageTypeResolver.class);

        EvolvableEntityModel<Task> taskModel = SimpleEvolvableEntityModel
                .forEntityClass(Task.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(Task.class))
                .commandHandler(typeResolver.resolve(CompleteTaskCommand.class).qualifiedName(),
                                (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((CompleteTaskCommand) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();


        EvolvableEntityModel<SalaryInformation> salaryInformationModel = SimpleEvolvableEntityModel
                .forEntityClass(SalaryInformation.class)
                .entityEvolver((entity, event, context) -> {
                    Object payload = event.getPayload();
                    if (payload instanceof RaiseGiven rg) {
                        // TODO, does not work with AnnotationBasedEventSourcedComponent
                        return new SalaryInformation(rg.newSalary(), entity.role());
                    }
                    return entity;
                })
                .commandHandler(typeResolver.resolve(GiveRaise.class).qualifiedName(),
                                (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context,
                                                                                           configuration);
                                    entity.handle((GiveRaise) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();

        EvolvableEntityModel<Employee> employeeModel = SimpleEvolvableEntityModel
                .forEntityClass(Employee.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(Employee.class))
                .commandHandler(typeResolver.resolve(CreateEmployee.class).qualifiedName(),
                                ((command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((CreateEmployee) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                }))
                .commandHandler(typeResolver.resolve(AssignTaskCommand.class).qualifiedName(),
                                ((command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((AssignTaskCommand) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                }))
                .addChild(ListEvolvableEntityChildModel.forEntityModel(
                                                               Employee.class,
                                                               taskModel
                                                       ).childEntityResolver(Employee::getTaskList)
                                                       .parentEntityEvolver((e, t) -> {
                                                           e.setTaskList(t);
                                                           return e;
                                                       })
                                                       .commandTargetMatcher((task, commandMessage) -> {
                                                           if (commandMessage.getPayload() instanceof CompleteTaskCommand assignTaskCommand) {
                                                               return task.getTaskId()
                                                                          .equals(assignTaskCommand.taskId());
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
                .addChild(SingleEvolvableEntityChildModel
                                  .forEntityClass(Employee.class, salaryInformationModel)
                                  .childEntityResolver(Employee::getSalary)
                                  .parentEntityEvolver(
                                          (e, s) -> {
                                              e.setSalary(s);
                                              return e;
                                          }
                                  )
                                  .build()
                )
                .build();

        EvolvableEntityModel<Customer> customerModel = SimpleEvolvableEntityModel
                .forEntityClass(Customer.class)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(Customer.class))
                .commandHandler(
                        typeResolver.resolve(CreateCustomer.class).qualifiedName(),
                        ((command, entity, context) -> {
                            EventAppender eventAppender = EventAppender.forContext(context, configuration);
                            entity.handle((CreateCustomer) command.getPayload(), eventAppender);
                            return MessageStream.empty().cast();
                        }))
                .build();

        EvolvableEntityModel<Person> personModel = PolymorphicEvolvableEntityModel
                .forAbstractEntityClass(Person.class)
                .addPolymorphicModel(employeeModel)
                .addPolymorphicModel(customerModel)
                .entityEvolver(new AnnotationBasedEventSourcedComponent<>(Person.class))
                .commandHandler(typeResolver.resolve(ChangeEmailAddress.class).qualifiedName(),
                                (command, entity, context) -> {
                                    EventAppender eventAppender = EventAppender.forContext(context, configuration);
                                    entity.handle((ChangeEmailAddress) command.getPayload(), eventAppender);
                                    return MessageStream.empty().cast();
                                })
                .build();

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
}
