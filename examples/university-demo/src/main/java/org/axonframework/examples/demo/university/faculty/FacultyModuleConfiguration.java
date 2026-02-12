package org.axonframework.examples.demo.university.faculty;

import org.axonframework.examples.demo.university.faculty.automation.allcoursesfullybookednotifier.AllCoursesFullyBookedNotifierConfiguration;
import org.axonframework.examples.demo.university.faculty.automation.studentsubscribednotifierplain.StudentSubscribedNotifierConfiguration;
import org.axonframework.examples.demo.university.faculty.read.coursestats.CourseStatsConfiguration;
import org.axonframework.examples.demo.university.faculty.write.enrollstudent.EnrollStudentInFacultyConfiguration;
import org.axonframework.examples.demo.university.shared.configuration.NotificationServiceConfiguration;
import org.axonframework.examples.demo.university.faculty.write.changecoursecapacity.ChangeCourseCapacityConfiguration;
import org.axonframework.examples.demo.university.faculty.write.createcourse.CreateCourseConfiguration;
import org.axonframework.examples.demo.university.faculty.write.createcourseplain.CreateCoursePlainConfiguration;
import org.axonframework.examples.demo.university.faculty.write.renamecourse.RenameCourseConfiguration;
import org.axonframework.examples.demo.university.faculty.write.subscribestudent.SubscribeStudentConfiguration;
import org.axonframework.examples.demo.university.faculty.write.subscribestudentmulti.SubscribeStudentMultiEntityConfiguration;
import org.axonframework.examples.demo.university.faculty.write.unsubscribestudent.UnsubscribeStudentConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class FacultyModuleConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        // shared infrastructure
        configurer = NotificationServiceConfiguration.configure(configurer);

        // Write side
        configurer = EnrollStudentInFacultyConfiguration.configure(configurer);
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CreateCoursePlainConfiguration.configure(configurer);
        configurer = RenameCourseConfiguration.configure(configurer);
        configurer = ChangeCourseCapacityConfiguration.configure(configurer);
        configurer = SubscribeStudentConfiguration.configure(configurer);
        configurer = SubscribeStudentMultiEntityConfiguration.configure(configurer);
        configurer = UnsubscribeStudentConfiguration.configure(configurer);

        // Read side
        configurer = CourseStatsConfiguration.configure(configurer);

        // Automations
        configurer = StudentSubscribedNotifierConfiguration.configure(configurer);
        configurer = AllCoursesFullyBookedNotifierConfiguration.configure(configurer);

        return configurer;
    }

    private FacultyModuleConfiguration() {
        // Prevent instantiation
    }
}
