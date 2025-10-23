package io.axoniq.demo.university.faculty.write.createcourse;

import io.axoniq.demo.university.faculty.events.CourseCreated;
import io.axoniq.demo.university.faculty.events.CourseRenamed;
import io.axoniq.demo.university.shared.ids.CourseId;
import org.axonframework.eventhandling.annotations.EventHandler;
import org.axonframework.eventhandling.annotations.SequencingPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates uniqueness of course names by maintaining an in-memory registry.
 * <p>
 * Uses {@link SequentialPolicy} to ensure events are processed sequentially,
 * eliminating the need for concurrent data structures or synchronization.
 */
@SequencingPolicy(type = SequentialPolicy.class)
public class CourseUniqueNameSetValidation {

    private final Map<CourseId, String> courseNames = new HashMap<>();

    @EventHandler
    public void evolve(CourseCreated event) {
        String courseName = event.name();

        if (courseNames.containsValue(courseName)) {
            throw new IllegalStateException(
                    "Course with name '" + courseName + "' already exists"
            );
        }

        courseNames.put(event.courseId(), courseName);
    }

    @EventHandler
    public void evolve(CourseRenamed event) {
        CourseId courseId = event.courseId();
        String newName = event.name();

        courseNames.computeIfPresent(courseId, (id, oldName) -> {
            boolean nameExists = courseNames.entrySet().stream()
                    .anyMatch(entry -> !entry.getKey().equals(courseId) && entry.getValue().equals(newName));

            if (nameExists) {
                throw new IllegalStateException(
                        "Course with name '" + newName + "' already exists"
                );
            }

            return newName;
        });
    }

}
