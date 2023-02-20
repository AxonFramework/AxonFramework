package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.CommandHandler;

/**
 * @author Allard Buijze
 */
public interface AnnotatedAggregateInterface {

    @CommandHandler
    void doSomething(TestCommand command);
}
