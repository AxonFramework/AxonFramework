package io.axoniq.demo.university.faculty.automation.allcoursesfullybookednotifier;

import io.axoniq.demo.university.shared.ids.FacultyId;
import org.axonframework.modelling.annotations.TargetEntityId;

public record SendAllCoursesFullyBookedNotification(@TargetEntityId FacultyId facultyId) {

}