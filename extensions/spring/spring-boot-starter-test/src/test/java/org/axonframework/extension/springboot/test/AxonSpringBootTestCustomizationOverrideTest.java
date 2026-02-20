/*
 * Copyright (c) 2010-2026. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extension.springboot.test;

import org.axonframework.messaging.commandhandling.annotation.CommandHandler;

import static org.junit.jupiter.api.Assertions.*;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.matchers.IgnoreField;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests verifying that a {@link AxonTestFixture.Customization} bean declared in a {@code @TestConfiguration}
 * overrides the default customization used by the {@link AxonTestFixture} bean registered by
 * {@link AxonSpringBootTest @AxonSpringBootTest}.
 * <p>
 * Verification is behavioral: a field filter customization is applied that ignores a randomly-valued field.
 * Without the customization, the fixture assertion would fail due to the mismatched random value.
 *
 * @author Mateusz Nowak
 */
@AxonSpringBootTest(
        classes = {
                AxonSpringBootTestCustomizationOverrideTest.TestApplication.class,
                AxonSpringBootTestCustomizationOverrideTest.CustomizationOverride.class
        },
        properties = "axon.axonserver.enabled=false"
)
class AxonSpringBootTestCustomizationOverrideTest {

    record Ping() {

    }

    record Pong(String message, String nonce) {

    }

    static class PingHandler {

        @CommandHandler
        Pong handle(Ping command) {
            return new Pong("handled", java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * Provides a {@link AxonTestFixture.Customization} that registers a field filter ignoring {@code Pong.nonce}.
     * This overrides the default (no-op) customization supplied by {@link AxonTestConfiguration}.
     */
    @TestConfiguration
    static class CustomizationOverride {

        @Bean
        AxonTestFixture.Customization axonTestFixtureCustomization() {
            return new AxonTestFixture.Customization()
                    .registerFieldFilter(new IgnoreField(Pong.class, "nonce"));
        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    static class TestApplication {

        @Bean
        PingHandler pingHandler() {
            return new PingHandler();
        }
    }

    @Autowired
    AxonTestFixture fixture;

    /**
     * Verifies that the {@link AxonTestFixture.Customization} bean from {@link CustomizationOverride} is picked up
     * by {@link AxonTestConfiguration} and used to configure the fixture.
     * <p>
     * {@code PingHandler} returns a {@code Pong} with a random {@code nonce}. If the field filter were NOT applied,
     * the fixture assertion {@code resultMessagePayload(new Pong("handled", "ignored"))} would fail due to the
     * mismatched nonce value. The test passing proves the customization was applied.
     */
    @Test
    void customizationFieldFilterIsAppliedToFixture() {
        fixture.when()
               .command(new Ping())
               .then()
               .success()
               .resultMessagePayload(new Pong("handled", "ignored"));
    }
}
