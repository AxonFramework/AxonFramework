package io.axoniq.demo.university.faculty.write.subscribe_student

import org.axonframework.eventsourcing.annotations.reflection.EntityCreator

class SubscribeStudentState @EntityCreator constructor() {

  fun decide(cmd: SubscribeStudent) : List<Any> {
    return listOf()
  }


}
