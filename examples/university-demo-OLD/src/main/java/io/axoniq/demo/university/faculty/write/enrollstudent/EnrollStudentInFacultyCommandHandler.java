package io.axoniq.demo.university.faculty.write.enrollstudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.Ids;
import io.axoniq.demo.university.faculty.events.StudentEnrolledInFaculty;
import org.axonframework.commandhandling.annotations.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;
import org.axonframework.modelling.annotations.InjectEntity;

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
