package org.axonframework.examples.university

import org.axonframework.common.configuration.ApplicationConfigurer
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.extension.AxonTestFixtureProvider
import org.axonframework.test.fixture.AxonTestFixture

data object TestFixtures {

    fun universityFixture(configuration: EventSourcingConfigurer.() -> ApplicationConfigurer): AxonTestFixtureProvider = AxonTestFixtureProvider {
        AxonTestFixture.with(
            UniversityKotlinApplication.configurer().configuration()
        ) { it.disableAxonServer() }
    }

}
