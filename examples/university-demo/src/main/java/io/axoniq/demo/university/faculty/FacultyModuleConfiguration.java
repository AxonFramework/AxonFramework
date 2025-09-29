package io.axoniq.demo.university.faculty;

import io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier.AllCoursesFullyBookedNotifierConfiguration;
import io.axoniq.demo.university.faculty.automation.studentsubscribednotifierplain.StudentSubscribedNotifierConfiguration;
import io.axoniq.demo.university.shared.configuration.NotificationServiceConfiguration;
import io.axoniq.demo.university.faculty.write.changecoursecapacity.ChangeCourseCapacityConfiguration;
import io.axoniq.demo.university.faculty.write.createcourse.CreateCourseConfiguration;
import io.axoniq.demo.university.faculty.write.createcourseplain.CreateCoursePlainConfiguration;
import io.axoniq.demo.university.faculty.write.renamecourse.RenameCourseConfiguration;
import io.axoniq.demo.university.faculty.write.subscribestudent.SubscribeStudentConfiguration;
import io.axoniq.demo.university.faculty.write.subscribestudentmulti.SubscribeStudentMultiEntityConfiguration;
import io.axoniq.demo.university.faculty.write.unsubscribestudent.UnsubscribeStudentConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

public class FacultyModuleConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        // shared infrastructure
        configurer = NotificationServiceConfiguration.configure(configurer);

        // Write side
        configurer = CreateCourseConfiguration.configure(configurer);
        configurer = CreateCoursePlainConfiguration.configure(configurer);
        configurer = RenameCourseConfiguration.configure(configurer);
        configurer = ChangeCourseCapacityConfiguration.configure(configurer);
        configurer = SubscribeStudentConfiguration.configure(configurer);
        configurer = SubscribeStudentMultiEntityConfiguration.configure(configurer);
        configurer = UnsubscribeStudentConfiguration.configure(configurer);

        // Read side
        // TBD

        // Automations
        configurer = StudentSubscribedNotifierConfiguration.configure(configurer);
        configurer = AllCoursesFullyBookedNotifierConfiguration.configure(configurer);

        return configurer;
    }

    private FacultyModuleConfiguration() {
        // Prevent instantiation
    }
}
