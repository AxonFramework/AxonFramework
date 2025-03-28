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

package org.axonframework.modelling.configuration;

import org.axonframework.modelling.SimpleRepository;
import org.axonframework.modelling.SimpleRepositoryEntityLoader;
import org.axonframework.modelling.SimpleRepositoryEntityPersister;
import org.axonframework.modelling.repository.AsyncRepository;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StateBasedEntityBuilder}.
 *
 * @author Steven van Beelen
 */
class StateBasedEntityBuilderTest {

    private SimpleRepositoryEntityLoader<CourseId, Course> testLoader;
    private SimpleRepositoryEntityPersister<CourseId, Course> testPersister;
    private AtomicBoolean constructedLoader;
    private AtomicBoolean constructedPersister;

    private StateBasedEntityBuilder<CourseId, Course> testSubject;

    @BeforeEach
    void setUp() {
        testLoader = (courseId, context) -> CompletableFuture.completedFuture(new Course(courseId));
        testPersister = (courseId, course, context) -> CompletableFuture.completedFuture(null);
        constructedLoader = new AtomicBoolean(false);
        constructedPersister = new AtomicBoolean(false);

        testSubject = StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                             .loader(c -> {
                                                 constructedLoader.set(true);
                                                 return testLoader;
                                             })
                                             .persister(c -> {
                                                 constructedPersister.set(true);
                                                 return testPersister;
                                             });
    }

    @Test
    void entityThrowsNullPointerExceptionForNullIdentifierType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> StateBasedEntityBuilder.entity(null, Course.class));
    }

    @Test
    void entityThrowsNullPointerExceptionForNullEntityType() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> StateBasedEntityBuilder.entity(CourseId.class, null));
    }

    @Test
    void repositoryThrowsNullPointerExceptionForNullRepository() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                                  .repository(null));
    }

    @Test
    void loaderThrowsNullPointerExceptionForNullLoader() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                                  .loader(null));
    }

    @Test
    void persisterThrowsNullPointerExceptionForNullPersister() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class,
                     () -> StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                                  .loader(c -> testLoader)
                                                  .persister(null));
    }

    @Test
    void entityNameCombinesIdentifierAndEntityTypeNames() {
        String expectedEntityName = "Course#CourseId";

        assertEquals(expectedEntityName, testSubject.entityName());
    }

    @Test
    void repositoryConstructsSimpleRepositoryForLoaderAndPersisterOperations() {
        AsyncRepository<CourseId, Course> result = testSubject.repository()
                                                              .build(ModellingConfigurer.create().build());

        assertInstanceOf(SimpleRepository.class, result);
        assertTrue(constructedLoader.get());
        assertTrue(constructedPersister.get());
    }

    @Test
    void repositoryProvidesRegisteredRepository() {
        AsyncRepository<CourseId, Course> testRepository =
                new SimpleRepository<>(CourseId.class, Course.class, testLoader, testPersister);

        AsyncRepository<CourseId, Course> result = StateBasedEntityBuilder.entity(CourseId.class, Course.class)
                                                                          .repository(c -> testRepository)
                                                                          .repository()
                                                                          .build(ModellingConfigurer.create().build());

        assertEquals(testRepository, result);
        assertFalse(constructedLoader.get());
        assertFalse(constructedPersister.get());
    }

    record CourseId() {

    }

    record Course(CourseId id) {

    }
}