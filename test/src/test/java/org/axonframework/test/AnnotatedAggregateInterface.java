package org.axonframework.test;

import org.axonframework.commandhandling.annotation.CommandHandler;

/**
 * @author Allard Buijze
 */
public interface AnnotatedAggregateInterface {

    @CommandHandler
    void doSomething(TestCommand command);
}
