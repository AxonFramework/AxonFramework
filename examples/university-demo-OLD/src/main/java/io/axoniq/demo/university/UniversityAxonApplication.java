package io.axoniq.demo.university;

import io.axoniq.demo.university.faculty.FacultyModuleConfiguration;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.createcourse.CreateCourse;
import io.axoniq.demo.university.faculty.write.renamecourse.RenameCourse;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.ApplicationConfigurer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.AsyncUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class UniversityAxonApplication {

    public ApplicationConfigurer<?> configurer() {
        var configurer = EventSourcingConfigurer.create();
        configurer = FacultyModuleConfiguration.configure(configurer);
        return configurer;
    }

    public static void main(String[] args) {
        var configurer = new UniversityAxonApplication()
                .configurer();
        var configuration = configurer.start();

        var courseId = CourseId.random();
        var createCourse = new CreateCourse(courseId, "Event Sourcing in Practice", 3);
        var renameCourse = new RenameCourse(courseId, "Advanced Event Sourcing");

        var commandGateway = configuration.getComponent(CommandGateway.class);
        commandGateway.sendAndWait(createCourse);
        commandGateway.sendAndWait(renameCourse);

//        var commandBus = configuration.getComponent(CommandBus.class);
//        inUnitOfWorkOnInvocation(context -> commandBus.dispatch(new GenericCommandMessage<>(new MessageType(createCourse.getClass()), createCourse), context));


    }


    private static void inUnitOfWorkOnInvocation(Function<ProcessingContext, CompletableFuture<?>> action) {
        var unitOfWork = new AsyncUnitOfWork();
        unitOfWork.onInvocation(action);
        unitOfWork.execute().join();
    }
}
