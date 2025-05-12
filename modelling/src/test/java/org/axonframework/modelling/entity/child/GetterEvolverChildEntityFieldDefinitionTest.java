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

package org.axonframework.modelling.entity.child;

import org.axonframework.modelling.entity.child.mock.RecordingChildEntity;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class GetterEvolverChildEntityFieldDefinitionTest {

    private final GetterEvolverChildEntityFieldDefinition<ParentEntity, RecordingChildEntity> testSubject = new GetterEvolverChildEntityFieldDefinition<>(
            ParentEntity::childEntity,
            ParentEntity::evolveBasedOnChildEntity
    );

    @Test
    void canRetrieveChildEntityForImmutableEntity() {
        RecordingChildEntity childEntity = new RecordingChildEntity("7826736");
        ParentEntity parentEntity = new ParentEntity(childEntity);

        assertEquals(childEntity, testSubject.getChildValue(parentEntity));
    }

    @Test
    void canEvolveParentEntityForImmutableEntity() {
        RecordingChildEntity childEntity = new RecordingChildEntity("2323802");
        ParentEntity parentEntity = new ParentEntity(childEntity);

        RecordingChildEntity newChildEntity = new RecordingChildEntity("1234567");
        ParentEntity evolvedParentEntity = testSubject
                .evolveParentBasedOnChildInput(parentEntity, newChildEntity);

        assertEquals(newChildEntity, evolvedParentEntity.childEntity());
    }

    @Test
    void canNotCreateWithNullGetter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new GetterEvolverChildEntityFieldDefinition<>(
                null,
                ParentEntity::evolveBasedOnChildEntity
        ));
    }

    @Test
    void canNotCreateWithNullSetter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new GetterEvolverChildEntityFieldDefinition<>(
                ParentEntity::childEntity,
                null
        ));
    }


    private record ParentEntity(
            RecordingChildEntity childEntity
    ) {

        public ParentEntity evolveBasedOnChildEntity(RecordingChildEntity childEntity) {
            return new ParentEntity(childEntity);
        }
    }
}