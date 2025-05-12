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

class GetterSetterChildEntityFieldDefinitionTest {

    private final GetterSetterChildEntityFieldDefinition<ParentEntity, RecordingChildEntity> testSubject = new GetterSetterChildEntityFieldDefinition<>(
            ParentEntity::getRecordingChildEntity,
            ParentEntity::setRecordingChildEntity
    );

    @Test
    void canRetrieveChildEntityForMutableEntity() {
        RecordingChildEntity childEntity = new RecordingChildEntity("7826736");
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setRecordingChildEntity(childEntity);

        assertEquals(childEntity, testSubject.getChildValue(parentEntity));
    }

    @Test
    void canEvolveParentEntityForMutableEntity() {
        RecordingChildEntity childEntity = new RecordingChildEntity("2323802");
        ParentEntity parentEntity = new ParentEntity();
        parentEntity.setRecordingChildEntity(childEntity);

        RecordingChildEntity newChildEntity = new RecordingChildEntity("1234567");
        ParentEntity evolvedParentEntity = testSubject.evolveParentBasedOnChildInput(parentEntity,
                                                                                     newChildEntity);

        assertEquals(newChildEntity, evolvedParentEntity.getRecordingChildEntity());
    }

    @Test
    void canNotCreateWithNullGetter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new GetterSetterChildEntityFieldDefinition<>(
                null,
                ParentEntity::setRecordingChildEntity
        ));
    }

    @Test
    void canNotCreateWithNullSetter() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new GetterSetterChildEntityFieldDefinition<>(
                ParentEntity::getRecordingChildEntity,
                null
        ));
    }


    private static class ParentEntity {

        private RecordingChildEntity childEntity;

        public RecordingChildEntity getRecordingChildEntity() {
            return childEntity;
        }

        public void setRecordingChildEntity(RecordingChildEntity childEntity) {
            this.childEntity = childEntity;
        }
    }
}