package io.axoniq.demo.university

import org.axonframework.configuration.ApplicationConfigurer
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer


fun main(args: Array<String>) {
}

class UniversityKotlinApplication {

  companion object {
    @JvmStatic
    fun configurer(): EventSourcingConfigurer = EventSourcingConfigurer.create()
  }

}
