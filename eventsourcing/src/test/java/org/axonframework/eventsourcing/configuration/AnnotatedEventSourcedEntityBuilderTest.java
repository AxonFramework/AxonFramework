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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.AsyncEventSourcingRepository;
import org.axonframework.eventsourcing.CriteriaResolver;
import org.axonframework.eventsourcing.annotation.CriteriaResolverDefinition;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactory;
import org.axonframework.eventsourcing.annotation.EventSourcedEntityFactoryDefinition;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.modelling.repository.AsyncRepository;
import org.junit.jupiter.api.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotatedEventSourcedEntityBuilder}.
 *
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 */
class AnnotatedEventSourcedEntityBuilderTest {

    private Configuration parentConfiguration;

    @BeforeEach
    void setUp() {
        parentConfiguration = EventSourcingConfigurer.create().build();
    }

    @Test
    void annotatedEntityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityBuilder.annotatedEntity(null, Course.class));
    }

    @Test
    void annotatedEntityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> EventSourcedEntityBuilder.annotatedEntity(CourseId.class, null));
    }

    @Test
    void annotatedEntityThrowsIllegalArgumentExceptionForNotAnnotatedEntity() {
        assertThrows(IllegalArgumentException.class,
                     () -> EventSourcedEntityBuilder.annotatedEntity(CourseId.class, CourseId.class));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        EventSourcedEntityBuilder<CourseId, Course> testSubject =
                EventSourcedEntityBuilder.annotatedEntity(CourseId.class, Course.class);

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void repositoryConstructsEventSourcingRepositoryForEntityFactory() {
        EventSourcedEntityBuilder<CourseId, Course> testSubject =
                EventSourcedEntityBuilder.annotatedEntity(CourseId.class, Course.class);

        AsyncRepository<CourseId, Course> result = testSubject.repository()
                                                              .build(parentConfiguration);

        assertInstanceOf(AsyncEventSourcingRepository.class, result);
    }

    @Test
    void customCriteriaResolverIsPresentOnResultingEventSourcingRepository() {
        ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);
        EventSourcedEntityBuilder<CourseId, CustomCriteriaResolverCourse> testSubject =
                EventSourcedEntityBuilder.annotatedEntity(CourseId.class, CustomCriteriaResolverCourse.class);

        AsyncRepository<CourseId, CustomCriteriaResolverCourse> result = testSubject.repository()
                                                                                    .build(parentConfiguration);

        assertInstanceOf(AsyncEventSourcingRepository.class, result);
        result.describeTo(componentDescriptor);
        verify(componentDescriptor).describeProperty(eq("criteriaResolver"), isA(CustomCriteriaResolver.class));
    }

    @Test
    void customEntityFactoryIsPresentOnResultingEventSourcingRepository() {
        ComponentDescriptor componentDescriptor = mock(ComponentDescriptor.class);
        EventSourcedEntityBuilder<CourseId, CustomEntityFactoryCourse> testSubject =
                EventSourcedEntityBuilder.annotatedEntity(CourseId.class, CustomEntityFactoryCourse.class);

        AsyncRepository<CourseId, CustomEntityFactoryCourse> result = testSubject.repository()
                                                                                 .build(parentConfiguration);

        assertInstanceOf(AsyncEventSourcingRepository.class, result);
        result.describeTo(componentDescriptor);
        verify(componentDescriptor).describeProperty(eq("entityFactory"), isA(CustomEventSourcedEntityFactory.class));
    }

    @Test
    void metaAnnotatedEventSourcedEntityConstructsAnEventSourcingRepository() {
        AsyncRepository<CourseId, MetaAnnotatedCourse> result =
                EventSourcedEntityBuilder.annotatedEntity(CourseId.class, MetaAnnotatedCourse.class)
                                         .repository()
                                         .build(parentConfiguration);

        assertInstanceOf(AsyncEventSourcingRepository.class, result);
    }

    record CourseId() {

    }

    @EventSourcedEntity
    record Course(CourseId id) {

    }

    @EventSourcedEntity(criteriaResolverDefinition = CustomCriteriaResolverDefinition.class)
    record CustomCriteriaResolverCourse(CourseId id) {

    }

    static class CustomCriteriaResolverDefinition implements CriteriaResolverDefinition {

        @Override
        public <E, ID> CriteriaResolver<ID> createEventCriteriaResolver(@Nonnull Class<E> entityType,
                                                                        @Nonnull Class<ID> idType,
                                                                        @Nonnull NewConfiguration configuration) {
            assertInstanceOf(NewConfiguration.class, configuration);
            return new CustomCriteriaResolver<>();
        }
    }

    private static class CustomCriteriaResolver<ID> implements CriteriaResolver<ID> {

        @Override
        public EventCriteria apply(ID id) {
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
                @Nonnull Class<CourseId> idType,
                @Nonnull NewConfiguration configuration
        ) {
            return new CustomEventSourcedEntityFactory();
        }
    }

    static class CustomEventSourcedEntityFactory
            implements EventSourcedEntityFactory<CourseId, CustomEntityFactoryCourse> {

        @Override
        public CustomEntityFactoryCourse createEntity(@Nonnull Class<CustomEntityFactoryCourse> entityType,
                                                      @Nonnull CourseId courseId) {
            return new CustomEntityFactoryCourse(courseId);
        }
    }

    @MetaAnnotatedEventSourcingEntity
    record MetaAnnotatedCourse(CourseId id) {

    }

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @EventSourcedEntity(tagKey = "metaAnnotated")
    public @interface MetaAnnotatedEventSourcingEntity {

    }
}