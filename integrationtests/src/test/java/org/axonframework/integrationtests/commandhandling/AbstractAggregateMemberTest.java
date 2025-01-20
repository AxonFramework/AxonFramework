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

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.*;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test whether aggregate member annotated collections of the same generic type be able to forward command.
 *
 * @author Somrak Monpengpinij
 */
public class AbstractAggregateMemberTest {

    private FixtureConfiguration<FactoryAggregate> fixture;
    private String factoryId = "factoryId";

    @BeforeEach
    public void setUp(){
        fixture = new AggregateTestFixture(FactoryAggregate.class);
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    public void initializingFactoryAggregate_ShouldBeAbleToInitialize(){
        fixture.givenNoPriorActivity()
                .when(new CreateFactoryCommand(factoryId))
                .expectEvents(new FactoryCreatedEvent(factoryId));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    public void forwardingCommandToAggregateMemberWithTheSameGenericType_ShouldForwardCommandToEmployeeAggregate(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new CreateTaskCommand(factoryId, "employeeId"))
                .expectEvents(new EmployeeTaskCreatedEvent(factoryId, "employeeId"));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    public void forwardingCommandToAggregateMemberWithTheSameGenericType_ShouldForwardCommandToManagerAggregate(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new CreateTaskCommand(factoryId, "managerId"))
                .expectEvents(new ManagerTaskCreatedEvent(factoryId, "managerId"));
    }

    @Test
    @Disabled("TODO #3064 - Deprecated UnitOfWork clean-up")
    public void sendCommandToNoneExistEntity_ShouldThrowAggregateEntityNotFoundException(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new CreateTaskCommand(factoryId, "none-exist-id"))
                .expectException(AggregateEntityNotFoundException.class);
    }

    private static abstract class Person {
        @EntityId
        public String personId;
    }

    private static class Employee extends Person {

        public Employee(){
        }

        public Employee(String personId){
            this.personId = personId;
        }

        @CommandHandler
        public void handle(CreateTaskCommand cmd){
            AggregateLifecycle.apply(new EmployeeTaskCreatedEvent(
                    cmd.getFactoryId(),
                    cmd.getPersonId()
            ));
        }

        @EventSourcingHandler
        public void on(EmployeeTaskCreatedEvent event){
        }

        public String getPersonId(){
            return personId;
        }

        @Override
        public int hashCode(){
            return this.personId.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Employee){
                Employee manager = (Employee) o;
                return manager.personId == this.personId;
            }
            return false;
        }
    }

    private static class Manager extends Person {

        public Manager(){
        }

        public Manager(String personId){
            this.personId = personId;
        }

        @CommandHandler
        public void handle(CreateTaskCommand cmd){
            AggregateLifecycle.apply(new ManagerTaskCreatedEvent(
                    cmd.getFactoryId(),
                    cmd.getPersonId()
            ));
        }

        @EventSourcingHandler
        public void on(ManagerTaskCreatedEvent event){
        }

        public String getPersonId(){
            return personId;
        }

        @Override
        public int hashCode(){
            return this.personId.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Manager){
                Manager manager = (Manager) o;
                return manager.personId == this.personId;
            }
            return false;
        }
    }

    @Aggregate
    private static class FactoryAggregate {
        @AggregateIdentifier
        public String factoryId;

        public List<Person> persons = new ArrayList<>();

        @AggregateMember(type = Employee.class)
        public List<Person> employees(){
            return persons.stream()
                          .filter(p -> p instanceof Employee)
                          .collect(Collectors.toList());
        }

        @AggregateMember(type = Manager.class)
        public List<Person> managers(){
            return persons.stream()
                          .filter(p -> p instanceof Manager)
                          .collect(Collectors.toList());
        }

        public String getFactoryId(){
            return factoryId;
        }

        public FactoryAggregate(){

        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateFactoryCommand cmd){
            AggregateLifecycle.apply(new FactoryCreatedEvent(
                    cmd.getFactoryId()
            ));
        }

        @EventSourcingHandler
        public void on(FactoryCreatedEvent event){
            this.factoryId = event.getFactoryId();
            this.persons.add(new Employee(
                    "employeeId"
            ));
            this.persons.add(new Manager(
                    "managerId"
            ));
        }

        @Override
        public int hashCode(){
            return this.factoryId.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof FactoryAggregate){
                FactoryAggregate manager = (FactoryAggregate) o;
                return manager.factoryId == this.factoryId;
            }
            return false;
        }
    }

    private static class CreateTaskCommand {
        @TargetAggregateIdentifier
        public String factoryId;
        public String personId;

        public CreateTaskCommand(){}

        public CreateTaskCommand(String factoryId, String personId){
            this.factoryId = factoryId;
            this.personId = personId;
        }

        public String getFactoryId(){
            return factoryId;
        }

        public String getPersonId(){
            return personId;
        }
    }

    private static class EmployeeTaskCreatedEvent {
        String factoryId;
        String personId;
        public EmployeeTaskCreatedEvent(String factoryId, String personId){
            this.factoryId = factoryId;
            this.personId = personId;
        }
    }

    private static class ManagerTaskCreatedEvent {
        String factoryId;
        String personId;
        public ManagerTaskCreatedEvent(String factoryId, String personId){
            this.factoryId = factoryId;
            this.personId = personId;
        }
    }

    private static class CreateFactoryCommand {
        @TargetAggregateIdentifier
        public String factoryId;
        public CreateFactoryCommand(String factoryId){
            this.factoryId = factoryId;
        }

        public String getFactoryId(){
            return factoryId;
        }
    }

    private static class FactoryCreatedEvent {
        public String factoryId;
        public FactoryCreatedEvent(String factoryId){
            this.factoryId = factoryId;
        }

        public String getFactoryId(){
            return factoryId;
        }
    }
}
