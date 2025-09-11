package io.axoniq.demo.university.faculty.read.coursestats;


import io.axoniq.demo.university.shared.ids.CourseId;

public record CoursesStatsReadModel(CourseId courseId, String name, int capacity, int subscribedStudents) {

    CoursesStatsReadModel name(String name){
        return new CoursesStatsReadModel(courseId, name, capacity, subscribedStudents);
    }

    CoursesStatsReadModel capacity(int capacity){
        return new CoursesStatsReadModel(courseId, name, capacity, subscribedStudents);
    }

    CoursesStatsReadModel subscribedStudents(int subscribedStudents){
        return new CoursesStatsReadModel(courseId, name, capacity, subscribedStudents);
    }

}
