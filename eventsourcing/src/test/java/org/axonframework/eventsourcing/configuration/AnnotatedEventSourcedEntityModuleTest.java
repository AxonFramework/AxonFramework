/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventsourcing.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.annotation.CriteriaResolverDefinition;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.messaging.eventstreaming.EventCriteria;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.repository.Repository;
import org.axonframework.common.util.StubLifecycleRegistry;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotatedEventSourcedEntityModule}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 */
class AnnotatedEventSourcedEntityModuleTest {

    private Configuration parentConfiguration;
    private StubLifecycleRegistry lifecycleRegistry;

    @BeforeEach
    void setUp() {
        parentConfiguration = EventSourcingConfigurer.create().build();
        lifecycleRegistry = new StubLifecycleRegistry();
    }

    @Test
    void annotatedEntityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityModule.autodetected(null, Course.class));
    }

    @Test
    void annotatedEntityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityModule.autodetected(CourseId.class, null));
    }

    @Test
    void annotatedEntityThrowsIllegalArgumentExceptionForNotAnnotatedEntity() {
        assertThrows(IllegalArgumentException.class,
                     () -> EventSourcedEntityModule.autodetected(CourseId.class, CourseId.class));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        EventSourcedEntityModule<CourseId, Course> testSubject =
                EventSourcedEntityModule.autodetected(CourseId.class, Course.class);

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void repositoryConstructsEventSourcingRepositoryForEntityFactory() {
        EventSourcedEntityModule.autodetected(CourseId.class, Course.class)
                                .build(parentConfiguration, lifecycleRegistry);
        lifecycleRegistry.start(parentConfiguration);

        StateManager stateManager = parentConfiguration.getComponent(StateManager.class);
        Repository<CourseId, Course> result = stateManager.repository(Course.class, CourseId.class);

        assertInstanceOf(EventSourcingRepository.class, result);
    }

    @Test
    void customCriteriaResolverIsPresentOnResultingEventSourcingRepository() {
        EventSourcedEntityModule.autodetected(CourseId.class, CustomCriteriaResolverCourse.class)
                                .build(parentConfiguration, lifecycleRegistry);
        lifecycleRegistry.start(parentConfiguration);
        ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);

        StateManager stateManager = parentConfiguration.getComponent(StateManager.class);
        Repository<CourseId, CustomCriteriaResolverCourse> result = stateManager.repository(CustomCriteriaResolverCourse.class,
                                                                                            CourseId.class);

        assertInstanceOf(EventSourcingRepository.class, result);
        result.describeTo(componentDescriptor);
        verify(componentDescriptor).describeProperty(eq("criteriaResolver"), isA(CustomCriteriaResolver.class));
    }

    @Test
    void customEntityFactoryIsPresentOnResultingEventSourcingRepository() {
        ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);
        EventSourcedEntityModule.autodetected(CourseId.class, CustomEntityFactoryCourse.class)
                                .build(parentConfiguration, lifecycleRegistry);
        lifecycleRegistry.start(parentConfiguration);

        StateManager stateManager = parentConfiguration.getComponent(StateManager.class);
        Repository<CourseId, CustomEntityFactoryCourse> result = stateManager.repository(CustomEntityFactoryCourse.class,
                                                                                         CourseId.class);

        assertInstanceOf(EventSourcingRepository.class, result);
        result.describeTo(componentDescriptor);
        verify(componentDescriptor).describeProperty(eq("entityFactory"), isA(CustomEventSourcedEntityFactory.class));
    }

    @Test
    void metaAnnotatedEventSourcedEntityConstructsAnEventSourcingRepository() {
        StateManager stateManager = parentConfiguration.getComponent(StateManager.class);
        EventSourcedEntityModule.autodetected(CourseId.class, MetaAnnotatedCourse.class)
                                .build(parentConfiguration, lifecycleRegistry);
        lifecycleRegistry.start(parentConfiguration);
        Repository<CourseId, MetaAnnotatedCourse> result = stateManager.repository(MetaAnnotatedCourse.class,
                                                                                   CourseId.class);

        assertInstanceOf(EventSourcingRepository.class, result);
    }

    record CourseId() {

    }

    @EventSourcedEntity
    record Course(CourseId id) {

        @EntityCreator
        public Course {
        }

    }

    @EventSourcedEntity(criteriaResolverDefinition = CustomCriteriaResolverDefinition.class)
    record CustomCriteriaResolverCourse(CourseId id) {

        @EntityCreator
        public CustomCriteriaResolverCourse {
        }

    }

    static class CustomCriteriaResolverDefinition implements CriteriaResolverDefinition {

        @Override
        public <E, ID> CriteriaResolver<ID> createEventCriteriaResolver(@Nonnull Class<E> entityType,
                                                                        @Nonnull Class<ID> idType,
                                                                        @Nonnull Configuration configuration) {
            assertInstanceOf(Configuration.class, configuration);
            return new CustomCriteriaResolver<>();
        }
    }

    private static class CustomCriteriaResolver<ID> implements CriteriaResolver<ID> {

        @Nonnull
        @Override
        public EventCriteria resolve(@Nonnull ID id, @Nonnull ProcessingContext context) {
            return EventCriteria.havingAnyTag();
        }
    }

    @EventSourcedEntity(entityFactoryDefinition = CustomEventSourcedEntityFactoryDefinition.class)
    record CustomEntityFactoryCourse(CourseId id) {

    }

    static class CustomEventSourcedEntityFactoryDefinition
            implements EventSourcedEntityFactoryDefinition<CustomEntityFactoryCourse, CourseId> {

        @Override
        public EventSourcedEntityFactory<CourseId, CustomEntityFactoryCourse> createFactory(
                @Nonnull Class<CustomEntityFactoryCourse> entityType,
                @Nonnull Set<Class<? extends CustomEntityFactoryCourse>> entitySubTypes,
                @Nonnull Class<CourseId> idType,
                @Nonnull Configuration configuration
        ) {
            return new CustomEventSourcedEntityFactory();
        }
    }

    static class CustomEventSourcedEntityFactory
            implements EventSourcedEntityFactory<CourseId, CustomEntityFactoryCourse> {

        @Override
        public @Nullable CustomEntityFactoryCourse create(
                @Nonnull CourseId courseId,
                @Nullable EventMessage firstEventMessage, @Nonnull ProcessingContext context) {
            return new CustomEntityFactoryCourse(courseId);
        }
    }

    @MetaAnnotatedEventSourcingEntity
    record MetaAnnotatedCourse(CourseId id) {

        @EntityCreator
        public MetaAnnotatedCourse {
        }
    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @EventSourcedEntity(tagKey = "metaAnnotated")
    public @interface MetaAnnotatedEventSourcingEntity {

    }
}