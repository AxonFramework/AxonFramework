/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.spring.authorization;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.shaded.com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.axonframework.test.matchers.Matchers.exactSequenceOf;
import static org.axonframework.test.matchers.Matchers.predicate;

class SecuredMethodMessageHandlerDefinitionTest {

    private static final UUID TEST_AGGREGATE_IDENTIFIER = UUID.randomUUID();
    private FixtureConfiguration<TestAggregate> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AggregateTestFixture<>(TestAggregate.class)
                .registerHandlerEnhancerDefinition(new SecuredMethodMessageHandlerDefinition());
    }

    @Test
    void shouldAllowWhenAuthorityMatch() {
        Map<String, java.util.HashSet<SimpleGrantedAuthority>> metaData = new java.util.HashMap<>();
        metaData.put("authorities", Sets.newHashSet(new SimpleGrantedAuthority("ROLE_aggregate.create")));
        testSubject.givenNoPriorActivity()
                   .when(new CreateAggregateCommand(TEST_AGGREGATE_IDENTIFIER), metaData)
                   .expectEventsMatching(exactSequenceOf(predicate(
                           eventMessage -> eventMessage.getPayloadType().isAssignableFrom(AggregateCreatedEvent.class)
                   )));
    }
    @Test
    void shouldDenyWhenAuthorityDoesNotMatch() {
        Map<String, java.util.HashSet<SimpleGrantedAuthority>> metaData = new java.util.HashMap<>();
        metaData.put("authorities", Sets.newHashSet(new SimpleGrantedAuthority("ROLE_anonymous")));
        testSubject.givenNoPriorActivity()
                   .when(new CreateAggregateCommand(TEST_AGGREGATE_IDENTIFIER),
                         metaData)
                   .expectException(SecurityException.class)
                   .expectExceptionMessage("Message denied");
    }
    @Test
    void shouldAllowUnannotatedMethods() {
        Map<String, Set<SimpleGrantedAuthority>> metaData = new java.util.HashMap<>();
        metaData.put("authorities", Sets.newHashSet(new SimpleGrantedAuthority("ROLE_anonymous")));
        testSubject.given(new AggregateCreatedEvent(TEST_AGGREGATE_IDENTIFIER))
                   .when(new UpdateAggregateCommand(TEST_AGGREGATE_IDENTIFIER), metaData)
                   .expectEventsMatching(exactSequenceOf(predicate(
                           eventMessage -> eventMessage.getPayloadType().isAssignableFrom(AggregateUpdatedEvent.class)
                   )));
    }
    @Test
    void shouldDenyWhenNoAuthorityPresent() {
        testSubject.givenNoPriorActivity()
                   .when(new CreateAggregateCommand(TEST_AGGREGATE_IDENTIFIER))
                   .expectException(SecurityException.class)
                   .expectExceptionMessage("Message denied");
    }
}