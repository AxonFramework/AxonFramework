package io.axoniq.demo.university.faculty.write.subscribestudent;

import io.axoniq.demo.university.faculty.FacultyTags;
import io.axoniq.demo.university.faculty.events.CourseCapacityChanged;
import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.StudentEnrolledFaculty;
import io.axoniq.demo.university.faculty.events.StudentSubscribed;
import io.axoniq.demo.university.faculty.events.StudentUnsubscribed;
import io.axoniq.demo.university.faculty.write.CourseId;
import io.axoniq.demo.university.faculty.write.StudentId;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;

class SubscribeStudentCommandHandler {

    private static final int MAX_COURSES_PER_STUDENT = 3;

    @EventSourcedEntity(tagKey = FacultyTags.COURSE_ID)
    public static class State {

        private StudentId studentId;
        private CourseId courseId;
        private int courseCapacity = 0;

        private int noOfCoursesStudentSubscribed = 0;
        private int noOfStudentsSubscribedToCourse = 0;

        private boolean alreadySubscribed = false;

        @EventSourcingHandler
        public void evolve(CourseCreated event) {
            this.courseId = new CourseId(event.courseId());
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        public void evolve(StudentEnrolledFaculty event) {
            this.studentId = new StudentId(event.studentId());
        }

        @EventSourcingHandler
        public void evolve(CourseCapacityChanged event) {
            this.courseCapacity = event.capacity();
        }

        @EventSourcingHandler
        public void evolve(StudentSubscribed event) {
            var enrolledStudentId = new StudentId(event.studentId());
            var enrolledCourseId = new CourseId(event.courseId());
            if (enrolledStudentId.equals(studentId) && enrolledCourseId.equals(courseId)) {
                alreadySubscribed = true;
            } else if (enrolledStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed++;
            } else {
                noOfStudentsSubscribedToCourse++;
            }
        }

        @EventSourcingHandler
        public void evolve(StudentUnsubscribed event) {
            var enrolledStudentId = new StudentId(event.studentId());
            var enrolledCourseId = new CourseId(event.courseId());
            if (enrolledStudentId.equals(studentId) && enrolledCourseId.equals(courseId)) {
                alreadySubscribed = false;
            } else if (enrolledStudentId.equals(studentId)) {
                noOfCoursesStudentSubscribed--;
            } else {
                noOfStudentsSubscribedToCourse--;
            }
        }
    }
}
