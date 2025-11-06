package io.axoniq.demo.university.faculty.write.unsubscribestudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.StudentSubscribedToCourse;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribedFromCourse;
import jakarta.annotation.Nonnull;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.conversion.MessageConverter;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.eventstreaming.Tag;
import org.axonframework.modelling.EntityIdResolver;
import org.axonframework.modelling.annotation.InjectEntity;

import java.util.List;

class UnsubscribeStudentFromCourseCommandHandler {

    @CommandHandler
    void handle(
            UnsubscribeStudentFromCourse command,
            @InjectEntity(idResolver = SubscriptionIdResolver.class) State state,
            EventAppender eventAppender
    ) {
        var events = decide(command, state);
        eventAppender.append(events);
    }

    private List<StudentUnsubscribedFromCourse> decide(UnsubscribeStudentFromCourse command, State state) {
        return state.subscribed
                ? List.of(new StudentUnsubscribedFromCourse(Ids.FACULTY_ID, command.studentId(), command.courseId()))
                : List.of();
    }

    @EventSourcedEntity
    static final class State {

        boolean subscribed = false;

        @EntityCreator
        public State() {
        }

        @EventSourcingHandler
        void evolve(StudentSubscribedToCourse event) {
            this.subscribed = true;
        }

        @EventSourcingHandler
        void evolve(StudentUnsubscribedFromCourse event) {
            this.subscribed = false;
        }

        @EventCriteriaBuilder
        private static EventCriteria resolveCriteria(SubscriptionId id) {
            var courseId = id.courseId().toString();
            var studentId = id.studentId().toString();
            return EventCriteria
                    .havingTags(Tag.of(FacultyTags.COURSE_ID, courseId), Tag.of(FacultyTags.STUDENT_ID, studentId))
                    .andBeingOneOfTypes(
                            StudentSubscribedToCourse.class.getName(),
                            StudentUnsubscribedFromCourse.class.getName()
                    );
        }
    }

    private static class SubscriptionIdResolver implements EntityIdResolver<SubscriptionId> {

        @Override
        @Nonnull
        public SubscriptionId resolve(@Nonnull Message command, @Nonnull ProcessingContext context) {
            var converter = context.component(MessageConverter.class);
            UnsubscribeStudentFromCourse payload = command.payloadAs(UnsubscribeStudentFromCourse.class, converter);
            if (payload == null) {
                throw new IllegalArgumentException("Can not resolve SubscriptionId from command");
            }
            return new SubscriptionId(payload.courseId(), payload.studentId());
        }
    }
}
