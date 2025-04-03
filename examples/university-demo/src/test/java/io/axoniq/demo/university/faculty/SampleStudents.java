package io.axoniq.demo.university.faculty;

import io.axoniq.demo.university.faculty.write.StudentId;

public class SampleStudents {

    public record Student(StudentId id, String firstName, String lastName) {

        public String rawId() {
            return id.raw();
        }
    }

    public static Student allard() {
        return new Student(StudentId.random(), "Allard", "Buijze");
    }

    public static Student mateusz() {
        return new Student(StudentId.random(), "Mateusz", "Nowak");
    }

    public static Student mitchell() {
        return new Student(StudentId.random(), "Mitchell", "Herrijgers");
    }

    public static Student steven() {
        return new Student(StudentId.random(), "Steven", "van Beelen");
    }
}
