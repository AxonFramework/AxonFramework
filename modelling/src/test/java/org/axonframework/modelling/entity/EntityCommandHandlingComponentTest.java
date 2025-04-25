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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.EntityIdResolver;
import org.axonframework.modelling.repository.ManagedEntity;
import org.axonframework.modelling.repository.Repository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.stubbing.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityCommandHandlingComponentTest {

    @Mock
    private Repository.LifecycleManagement<String, TestEntity> repository;

    @Mock
    private EntityModel<TestEntity> entityModel;

    @Mock
    private EntityIdResolver<String> idResolver;

    CommandMessage<?> commandMessage = mock(CommandMessage.class);
    String entityId = "myEntityId456";
    ManagedEntity<String, TestEntity> managedEntity = mock(ManagedEntity.class);
    TestEntity mockEntity = mock(TestEntity.class);

    private EntityCommandHandlingComponent<String, TestEntity> testComponent;

    @BeforeEach
    void setUp() {
        testComponent = new EntityCommandHandlingComponent<>(
                repository,
                entityModel,
                idResolver
        );
    }

    @Test
    void returnsQualifiedNamesFromEntityModel() {
        Set<QualifiedName> supportedCommands = Set.of(
                new QualifiedName("command1"),
                new QualifiedName("command3"),
                new QualifiedName("command5")
        );
        when(entityModel.supportedCommands()).thenReturn(supportedCommands);

        Set<QualifiedName> result = testComponent.supportedCommands();
        Assertions.assertEquals(supportedCommands, result);
    }

    @Test
    void loadsEntityBasedOnIdAndForwardsCommand() {

        when(idResolver.resolve(eq(commandMessage), any())).thenReturn(entityId);
        when(managedEntity.entity()).thenReturn(mockEntity);
        when(repository.load(eq(entityId), any())).thenReturn(CompletableFuture.completedFuture(managedEntity));
        CommandResultMessage<?> resultMessage = mock(GenericCommandResultMessage.class);
        MessageStream.Single<CommandResultMessage<?>> entityResult = MessageStream.just(resultMessage);
        when(entityModel.handle(eq(commandMessage), eq(mockEntity), any())).thenReturn(entityResult);

        MessageStream.Single<CommandResultMessage<?>> componentResult = testComponent.handle(commandMessage,
                                                                                                       mock(ProcessingContext.class));

        verify(entityModel).handle(eq(commandMessage), eq(mockEntity), any());
        Assertions.assertEquals(resultMessage, componentResult.asCompletableFuture().join().message());
    }

    @Test
    void failureToResolveIdWillResultInFailedMessageStream() {
        when(idResolver.resolve(eq(commandMessage), any())).thenThrow(new RuntimeException("Failed to resolve ID"));


        MessageStream.Single<? extends CommandResultMessage<?>> componentResult = testComponent.handle(commandMessage,
                                                                                                       mock(ProcessingContext.class));

        var exception = assertThrows(RuntimeException.class, () -> componentResult.asCompletableFuture().join());
        assertEquals("Failed to resolve ID", exception.getCause().getMessage());
    }

    @Test
    void failureToLoadEntityWillResultInFailedMessageStream() {
        when(idResolver.resolve(eq(commandMessage), any())).thenReturn(entityId);
        when(managedEntity.entity()).thenReturn(mockEntity);
        when(repository.load(eq(entityId), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException(
                "Failed to load entity")));


        MessageStream.Single<? extends CommandResultMessage<?>> componentResult = testComponent.handle(commandMessage,
                                                                                                       mock(ProcessingContext.class));

        var exception = assertThrows(RuntimeException.class, () -> componentResult.asCompletableFuture().join());
        assertEquals("Failed to load entity", exception.getCause().getMessage());
    }


    private static class TestEntity {
        // Test entity class for generic type resolution
    }
}