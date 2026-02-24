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

package org.axonframework.messaging.core.unitofwork.transaction.jdbc;

import org.axonframework.common.jdbc.ConnectionExecutor;
import org.axonframework.common.tx.TransactionalExecutor;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link JdbcTransactionalExecutorProvider}.
 *
 * @author John Hendrikx
 */
@ExtendWith(MockitoExtension.class)
class JdbcTransactionalExecutorProviderTest {
    final StubProcessingContext processingContext = new StubProcessingContext();

    @Mock DataSource dataSource;
    @Mock ConnectionExecutor connectionExecutor;
    @Mock Connection connection;
    @InjectMocks JdbcTransactionalExecutorProvider provider;

    @Nested
    class WhenProcessingContextAvailable {
        @Test
        void shouldGetTransactionalExecutorIfPresent() {
            processingContext.putResource(JdbcTransactionalExecutorProvider.SUPPLIER_KEY, () -> connectionExecutor);

            TransactionalExecutor<Connection> executor = provider.getTransactionalExecutor(processingContext);

            assertThat(executor).isEqualTo(connectionExecutor);
        }

        @Test
        void shouldThrowExceptionWhenExecutorNotPresent() {
            assertThatThrownBy(() -> provider.getTransactionalExecutor(processingContext))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class WhenGetTransactionalExecutorIsCalledWithoutProcessingContext {
        TransactionalExecutor<Connection> executor;

        @BeforeEach
        void beforeEach() {
            executor = provider.getTransactionalExecutor(null);
        }

        @Test
        void shouldNotBeNull() {
            assertThat(executor).isNotNull();
        }

        @Nested
        class AndNoConnectionIsAvailable {
            @BeforeEach
            void beforeEach() throws SQLException {
                when(dataSource.getConnection()).thenThrow(SQLException.class);
            }

            @Test
            void shouldReturnFailedFuture() {
                CompletableFuture<String> future = executor.apply(connection -> "Hello");

                assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(SQLException.class);
            }
        }

        @Nested
        class AndAConnectionIsAvailable {
            @BeforeEach
            void beforeEach() throws SQLException {
                when(dataSource.getConnection()).thenReturn(connection);
            }

            @Test
            void onSuccesfulCallbackShouldSetConnectionToAutoCommitAndCommitAndCloseIt() throws Exception {
                CompletableFuture<String> future = executor.apply(connection -> "Hello");

                future.join();

                assertThat(future.get()).isEqualTo("Hello");

                verify(connection).setAutoCommit(false);
                verify(connection).commit();
                verify(connection).close();
            }

            @Test
            void onFailedCallbackShouldSetConnectionToAutoCommitAndRollbackAndCloseIt() throws SQLException {
                CompletableFuture<Void> future = executor.accept(connection -> { throw new IllegalArgumentException(); });

                assertThatThrownBy(future::join)
                    .isInstanceOf(CompletionException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class);

                verify(connection).setAutoCommit(false);
                verify(connection).rollback();
                verify(connection).close();
            }
        }
    }
}
