package org.axonframework.integrationtests.commandhandling;


import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.*;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AbstractAggregateMemberTest {
    private FixtureConfiguration<FactoryAggregate> fixture;
    private String factoryId = "factoryId";

    @BeforeEach
    public void setUp(){
        fixture = new AggregateTestFixture(FactoryAggregate.class);
    }

    @Test
    public void testInitFactoryAggregate_ShouldHandleCommandNormally(){
        fixture.givenNoPriorActivity()
                .when(new CreateFactoryCommand(factoryId))
                .expectEvents(new FactoryCreatedEvent(factoryId));
    }

    @Test
    public void testEmployees_ShouldBeAbleToHandleCommand(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new SomethingCommand(factoryId, "employeeId"))
                .expectEvents(new SomethingEvent(factoryId, "employeeId"));
    }

    @Test
    public void testManagers_ShouldBeAbleToHandleCommand(){
        fixture.givenCommands(new CreateFactoryCommand(factoryId))
                .when(new SomethingCommand(factoryId, "managerId"))
                .expectEvents(new SomethingEvent(factoryId, "managerId"));
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
        public void handle(SomethingCommand cmd){
            AggregateLifecycle.apply(new SomethingEvent(cmd.factoryId, cmd.personId));
        }

        @EventSourcingHandler
        public void on(SomethingEvent event){
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
        public void handle(SomethingCommand cmd){
            AggregateLifecycle.apply(new SomethingEvent(cmd.factoryId, cmd.personId));
        }

        @EventSourcingHandler
        public void on(SomethingEvent event){
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
        public FactoryAggregate(CreateFactoryCommand cmd){
            AggregateLifecycle.apply(new FactoryCreatedEvent(
                    cmd.factoryId
            ));
        }

        @EventSourcingHandler
        public void on(FactoryCreatedEvent event){
            this.factoryId = event.factoryId;
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

    private static class SomethingCommand {
        @TargetAggregateIdentifier
        public String factoryId;
        public String personId;

        public SomethingCommand(){}

        public SomethingCommand(String factoryId, String personId){
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

    private static class SomethingEvent {
        String factoryId;
        String personId;
        public SomethingEvent(String factoryId, String personId){
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
    }

    private static class FactoryCreatedEvent {
        public String factoryId;
        public FactoryCreatedEvent(String factoryId){
            this.factoryId = factoryId;
        }
    }
}
