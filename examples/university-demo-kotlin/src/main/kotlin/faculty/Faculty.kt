package io.axoniq.demo.university.faculty

data object Faculty {
  data object Tag {
    const val STUDENT_ID: String = "studentId"
    const val COURSE_ID: String = "courseId"
  }
}
