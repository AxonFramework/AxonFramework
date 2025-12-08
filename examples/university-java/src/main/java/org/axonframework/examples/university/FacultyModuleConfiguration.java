/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
