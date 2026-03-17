package org.axonframework.examples.demo.university.faculty.automation.allcoursesfullybookednotifier;

import org.axonframework.examples.demo.university.shared.ids.FacultyId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record SendAllCoursesFullyBookedNotification(@TargetEntityId FacultyId facultyId) {

}