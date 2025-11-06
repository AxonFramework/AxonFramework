package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.shared.ids.FacultyId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record SendAllCoursesFullyBookedNotification(@TargetEntityId FacultyId facultyId) {

}