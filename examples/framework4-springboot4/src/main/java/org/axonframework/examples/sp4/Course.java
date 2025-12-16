package org.axonframework.examples.sp4;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class Course {

    @AggregateIdentifier
    private String id;

    protected Course() {
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.ALWAYS)
    public void create(CreateCourse cmd) {
        apply(new CourseCreated(cmd.id(), cmd.name()));
    }

    @EventSourcingHandler
    public void on(CourseCreated evt) {
        id = evt.id();
    }
}
