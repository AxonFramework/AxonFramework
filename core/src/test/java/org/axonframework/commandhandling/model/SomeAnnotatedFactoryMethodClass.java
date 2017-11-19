package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.CommandHandler;

/**
 * Test Aggregate demonstrating the use of a factory method for @{@link Aggregate} instantiation.
 *
 * @author Christophe Bouhier
 */
public class SomeAnnotatedFactoryMethodClass {

    @AggregateIdentifier
    private String id = "id";

    SomeAnnotatedFactoryMethodClass(String id) {
        this.id = id;
    }

    @CommandHandler
    public static SomeAnnotatedFactoryMethodClass factoryMethod(String id) {
        return new SomeAnnotatedFactoryMethodClass(id);
    }

    public String getId() {
        return id;
    }
}