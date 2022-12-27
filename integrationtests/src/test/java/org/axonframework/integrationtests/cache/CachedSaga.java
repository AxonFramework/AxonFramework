/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.integrationtests.cache;

import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Test saga used by the {@link CachingIntegrationTestSuite}.
 *
 * @author Steven van Beelen
 */
public class CachedSaga {

    private String name;
    private List<Object> state;
    private int numberOfAssociations;
    private Random random;
    private Integer associationToRemove;

    @StartSaga
    @SagaEventHandler(associationProperty = "id")
    public void on(SagaCreatedEvent event) {
        this.name = event.name;
        this.state = new ArrayList<>();
        this.numberOfAssociations = event.numberOfAssociations;
        this.random = new Random();

        for (int i = 0; i < numberOfAssociations; i++) {
            SagaLifecycle.associateWith(event.id + i, i);
        }
    }

    @SagaEventHandler(associationProperty = "id")
    public void on(VeryImportantEvent event) {
        state.add(event.stateEntry);
        if (associationToRemove == null) {
            associationToRemove = random.nextInt(numberOfAssociations);
            SagaLifecycle.removeAssociationWith(event.id + associationToRemove, associationToRemove);
        } else {
            SagaLifecycle.associateWith(event.id + associationToRemove, associationToRemove);
            associationToRemove = null;
        }
    }

    @SagaEventHandler(associationProperty = "id")
    public void on(SagaEndsEvent event) {
        if (event.shouldEnd) {
            SagaLifecycle.end();
        }
    }

    public String getName() {
        return name;
    }

    public List<Object> getState() {
        return state;
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class SagaCreatedEvent {

        private final String id;
        private final String name;
        private final int numberOfAssociations;

        public SagaCreatedEvent(String id, String name, int numberOfAssociations) {
            this.id = id;
            this.name = name;
            this.numberOfAssociations = numberOfAssociations;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class VeryImportantEvent {

        private final String id;
        private final Object stateEntry;

        public VeryImportantEvent(String id, Object stateEntry) {
            this.id = id;
            this.stateEntry = stateEntry;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    public static class SagaEndsEvent {

        private final String id;
        private final boolean shouldEnd;

        public SagaEndsEvent(String id, boolean shouldEnd) {
            this.id = id;
            this.shouldEnd = shouldEnd;
        }

        public String getId() {
            return id;
        }
    }
}
