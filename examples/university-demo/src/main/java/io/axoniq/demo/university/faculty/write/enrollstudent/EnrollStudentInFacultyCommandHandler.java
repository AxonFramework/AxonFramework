package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.modelling.annotation.InjectEntity;

public class EnrollStudentInFacultyCommandHandler {

    @EventSourcedEntity(tagKey = FacultyTags.STUDENT_ID)
    public static class Student {

        private boolean exists;

        @EntityCreator
        private Student() {
        }

        @EventSourcingHandler
        void evolve(StudentEnrolledInFaculty enrolledInFaculty) {
            exists = true;
        }
    }

    @CommandHandler
    void handle(EnrollStudentInFaculty command, @InjectEntity(idProperty = "studentId") Student student, EventAppender eventAppender) {
        if (!student.exists) {
            eventAppender.append(new StudentEnrolledInFaculty(Ids.FACULTY_ID, command.studentId(), command.firstName(), command.lastName()));
        }
    }
}
