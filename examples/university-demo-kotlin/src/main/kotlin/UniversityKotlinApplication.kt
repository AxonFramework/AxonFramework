package org.axonframework.examples.university

import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.conversion.DelegatingGeneralConverter
import org.axonframework.conversion.GeneralConverter
import org.axonframework.conversion.jackson2.Jackson2Converter


fun main(args: Array<String>) {
    UniversityKotlinApplication.configurer().start()
}

class UniversityKotlinApplication {

    companion object {
        @JvmStatic
        fun configurer(): EventSourcingConfigurer = EventSourcingConfigurer.create()
            .componentRegistry {
                it.registerComponent(GeneralConverter::class.java) { _ ->
                    DelegatingGeneralConverter(Jackson2Converter())
                }
            }
    }

}
