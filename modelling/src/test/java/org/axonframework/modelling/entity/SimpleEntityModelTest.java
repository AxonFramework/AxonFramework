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

package org.axonframework.modelling.entity;

import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleEntityModelTest {

    public static final QualifiedName PARENT_ONLY_COMMAND = new QualifiedName("ParentCommand");
    public static final QualifiedName SHARED_COMMAND = new QualifiedName("SharedCommand");
    public static final QualifiedName CHILD_ONLY_COMMAND = new QualifiedName("ChildOnlyCommand");

    private EntityChildModel<TestEntity, TestEntity> childModelMock = mock(EntityChildModel.class);
    private EntityCommandHandler<TestEntity> parentEntityCommandHandler = mock(EntityCommandHandler.class);
    private EntityEvolver<TestEntity> parentEntityEvolver = mock(EntityEvolver.class);

    @BeforeEach
    void setUp() {
        when(childModelMock.supportedCommands()).thenReturn(Set.of(CHILD_ONLY_COMMAND, SHARED_COMMAND));
        when(parentEntityEvolver.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(1));
        when(childModelMock.handle(any(),
                                   any(),
                                   any())).thenReturn(MessageStream.just(new GenericCommandResultMessage<>(new MessageType(
                String.class), "child")));
        when(parentEntityCommandHandler.handle(any(),
                                               any(),
                                               any())).thenReturn(MessageStream.just(new GenericCommandResultMessage<>(
                new MessageType(String.class),
                "parent")));
    }

    private final EntityModel<TestEntity> entityModel = SimpleEntityModel
            .forEntityClass(TestEntity.class)
            .entityEvolver(parentEntityEvolver)
            .commandHandler(SHARED_COMMAND, parentEntityCommandHandler)
            .commandHandler(PARENT_ONLY_COMMAND, parentEntityCommandHandler)
            .addChild(childModelMock)
            .build();

    @Test
    void commandForParentWillOnlyCallParent() {
        GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(PARENT_ONLY_COMMAND),
                                                                            "myPayload");
        TestEntity entity = new TestEntity();
        StubProcessingContext context = new StubProcessingContext();
        MessageStream.Single<CommandResultMessage<?>> result = entityModel.handle(command, entity, context);

        assertEquals("parent", result.asCompletableFuture().join().message().getPayload());
        verify(parentEntityCommandHandler).handle(command, entity, context);
        verify(childModelMock, times(0)).handle(command, entity, context);
    }

    @Test
    void commandDefinedInChildWillOnlyCallChild() {
        GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(CHILD_ONLY_COMMAND),
                                                                            "myPayload");
        TestEntity entity = new TestEntity();
        StubProcessingContext context = new StubProcessingContext();
        MessageStream.Single<CommandResultMessage<?>> result = entityModel.handle(command, entity, context);

        assertEquals("child", result.asCompletableFuture().join().message().getPayload());
        verify(childModelMock).handle(command, entity, context);
        verify(parentEntityCommandHandler, times(0)).handle(command, entity, context);
    }

    private static class TestEntity {

    }
}