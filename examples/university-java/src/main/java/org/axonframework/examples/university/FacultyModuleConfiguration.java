package org.axonframework.examples.university;

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.examples.university.automation.CourseFullyBookedNotifierConfiguration;
import org.axonframework.examples.university.read.coursestats.projection.CourseStatsConfiguration;
import org.axonframework.examples.university.shared.notification.NotificationConfiguration;
import org.axonframework.examples.university.write.changecoursecapacity.ChangeCourseCapacityConfiguration;
import org.axonframework.examples.university.write.createcourse.CreateCourseConfiguration;
import org.axonframework.examples.university.write.enrollstudent.EnrollStudentInFacultyConfiguration;
import org.axonframework.examples.university.write.renamecourse.RenameCourseConfiguration;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentConfiguration;
import org.axonframework.examples.university.write.unsubscribestudent.UnsubscribeStudentConfiguration;

public class FacultyModuleConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {

        // shared infrastructure
        configurer = NotificationConfiguration.configure(configurer);

        // Write side
        configurer = EnrollStudentInFacultyConfiguration.configure(configurer);
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = RenameCourseConfiguration.configure(configurer);
        configurer = ChangeCourseCapacityConfiguration.configure(configurer);
        configurer = SubscribeStudentConfiguration.configure(configurer);
        configurer = UnsubscribeStudentConfiguration.configure(configurer);

        // Read side
        configurer = CourseStatsConfiguration.configure(configurer);

        // Automations
        configurer = CourseFullyBookedNotifierConfiguration.configure(configurer);

        return configurer;
    }

    private FacultyModuleConfiguration() {
        // Prevent instantiation
    }
}
