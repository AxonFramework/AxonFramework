package io.axoniq.demo.university

import io.axoniq.demo.university.faculty.write.create_course.registerCreateCourse
import org.axonframework.configuration.ApplicationConfigurer
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer

data object TestFixtures {

  val APPLICATION_CONFIGURER: ApplicationConfigurer = EventSourcingConfigurer.create()
    .registerCreateCourse()

}
