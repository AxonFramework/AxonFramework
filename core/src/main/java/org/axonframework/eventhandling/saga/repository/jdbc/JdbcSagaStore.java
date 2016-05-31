/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga.repository.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.DataSourceConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.AssociationValues;
import org.axonframework.eventhandling.saga.SagaStorageException;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.saga.repository.jpa.SagaEntry;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

import static org.axonframework.common.jdbc.JdbcUtils.closeQuietly;


/**
 * Jdbc implementation of the Saga Repository.
 * <p/>
 * <p/>
 *
 * @author Allard Buijze
 * @author Kristian Rosenvold
 * @since 2.1
 */
public class JdbcSagaStore implements SagaStore<Object> {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSagaStore.class);

    private Serializer serializer;
    private final ConnectionProvider connectionProvider;

    private final SagaSqlSchema sqldef;

    /**
     * Initializes a Saga Repository, using given <code>connectionProvider</code> to obtain connections to the
     * database, using a Generic SQL Schema.
     *
     * @param connectionProvider The data source to obtain connections from
     */
    public JdbcSagaStore(ConnectionProvider connectionProvider) {
        this(connectionProvider, new GenericSagaSqlSchema());
    }

    /**
     * Initializes a Saga Repository, using given <code>dataSource</code> to obtain connections to the database, and
     * given <code>sqldef</code> to execute SQL statements.
     *
     * @param dataSource The data source to obtain connections from
     * @param sqldef     The definition of SQL operations to execute
     */
    public JdbcSagaStore(DataSource dataSource, SagaSqlSchema sqldef) {
        this(new UnitOfWorkAwareConnectionProviderWrapper(new DataSourceConnectionProvider(dataSource)), sqldef);
    }

    /**
     * Initializes a Saga Repository, using given <code>connectionProvider</code> to obtain connections to the
     * database, and given <code>sqldef</code> to execute SQL statements.
     *
     * @param connectionProvider The provider to obtain connections from
     * @param sqldef             The definition of SQL operations to execute
     */
    public JdbcSagaStore(ConnectionProvider connectionProvider, SagaSqlSchema sqldef) {
        this(connectionProvider, sqldef, new XStreamSerializer());
    }

    /**
     * Initializes a Saga Repository, using given <code>connectionProvider</code> to obtain connections to the
     * database, and given <code>sqldef</code> to execute SQL statements and <code>serializer</code> to serialize
     * Sagas.
     *
     * @param connectionProvider The provider to obtain connections from
     * @param sqldef             The definition of SQL operations to execute
     * @param serializer         The serializer to serialize and deserialize Saga instances with
     */
    public JdbcSagaStore(ConnectionProvider connectionProvider,
                         SagaSqlSchema sqldef, Serializer serializer) {
        this.connectionProvider = connectionProvider;
        this.sqldef = sqldef;
        this.serializer = serializer;
    }

    @Override
    public <S> Entry<S> loadSaga(Class<S> sagaType, String sagaIdentifier) {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_loadSaga(conn, sagaIdentifier);
            resultSet = statement.executeQuery();

            SerializedObject<?> serializedSaga = null;
            if (resultSet.next()) {
                serializedSaga = sqldef.readSerializedSaga(resultSet);
            }
            if (serializedSaga == null) {
                return null;
            }
            S loadedSaga = serializer.deserialize(serializedSaga);
            if (logger.isDebugEnabled()) {
                logger.debug("Loaded saga id [{}] of type [{}]", sagaIdentifier, loadedSaga.getClass().getName());
            }

            Set<AssociationValue> associations = sqldef.readAssociationValues(sqldef.sql_findAssociations(conn, sagaIdentifier, sagaTypeName(sagaType)).executeQuery());

            return new EntryImpl<>(associations, loadedSaga);
        } catch (SQLException e) {
            throw new SagaStorageException("Exception while loading a Saga", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(resultSet);
            closeQuietly(conn);
        }
    }

    @Override
    public Set<String> findSagas(Class<?> sagaType, AssociationValue associationValue) {
        ResultSet resultSet = null;
        PreparedStatement statement = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_findAssocSagaIdentifiers(conn, associationValue.getKey(),
                                                            associationValue.getValue(), sagaTypeName(sagaType));
            resultSet = statement.executeQuery();
            Set<String> result = new TreeSet<>();
            while (resultSet.next()) {
                result.add(resultSet.getString(1));
            }
            return result;
        } catch (SQLException e) {
            throw new SagaStorageException("Exception while reading saga associations", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(resultSet);
            closeQuietly(conn);
        }
    }

    @Override
    public void deleteSaga(Class<?> sagaType, String sagaIdentifier, Set<AssociationValue> associationValues) {
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement1 = sqldef.sql_deleteAssociationEntries(conn, sagaIdentifier);
            statement2 = sqldef.sql_deleteSagaEntry(conn, sagaIdentifier);
            statement1.executeUpdate();
            statement2.executeUpdate();
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to delete a saga entry", e);
        } finally {
            closeQuietly(statement1);
            closeQuietly(statement2);
            closeQuietly(conn);
        }
    }

    @Override
    public void updateSaga(Class<?> sagaType, String sagaIdentifier, Object saga, TrackingToken token, AssociationValues associationValues) {
        SagaEntry<?> entry = new SagaEntry<>(saga, sagaIdentifier, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Updating saga id {} as {}", sagaIdentifier, new String(entry.getSerializedSaga(),
                                                                                 Charset.forName("UTF-8")));
        }

        int updateCount = 0;
        PreparedStatement statement = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_updateSaga(conn,
                                              entry.getSagaId(),
                                              entry.getSerializedSaga(),
                                              entry.getSagaType(),
                                              entry.getRevision()
            );
            updateCount = statement.executeUpdate();
            if (updateCount != 0) {
                for (AssociationValue associationValue : associationValues.addedAssociations()) {
                    closeQuietly(statement);
                    statement = sqldef.sql_storeAssocValue(conn,
                                                           associationValue.getKey(),
                                                           associationValue.getValue(),
                                                           sagaTypeName(sagaType),
                                                           sagaIdentifier);
                    statement.executeUpdate();
                }
                for (AssociationValue associationValue : associationValues.removedAssociations()) {
                    closeQuietly(statement);
                    statement = sqldef.sql_removeAssocValue(conn,
                                                            associationValue.getKey(),
                                                            associationValue.getValue(),
                                                            sagaTypeName(sagaType),
                                                            sagaIdentifier);
                    statement.executeUpdate();

                }
            }
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to update a saga", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(conn);
        }

        if (updateCount == 0) {
            logger.warn("Expected to be able to update a Saga instance, but no rows were found. Inserting instead.");
            insertSaga(sagaType, sagaIdentifier, saga, token, associationValues.asSet());
        }
    }

    @Override
    public void insertSaga(Class<?> sagaType, String sagaIdentifier, Object saga, TrackingToken token, Set<AssociationValue> associationValues) {
        SagaEntry<?> entry = new SagaEntry<>(saga, sagaIdentifier, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Storing saga id {} as {}", sagaIdentifier, new String(entry.getSerializedSaga(),
                                                                                Charset.forName("UTF-8")));
        }
        Connection conn = null;
        PreparedStatement statement = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_storeSaga(conn, entry.getSagaId(), entry.getRevision(), entry.getSagaType(),
                                             entry.getSerializedSaga());
            statement.executeUpdate();

            for (AssociationValue associationValue : associationValues) {
                closeQuietly(statement);
                statement = sqldef.sql_storeAssocValue(conn,
                                                       associationValue.getKey(),
                                                       associationValue.getValue(),
                                                       sagaTypeName(sagaType),
                                                       sagaIdentifier);
                statement.executeUpdate();
            }

        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to store a Saga Entry", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(conn);
        }
    }

    private String sagaTypeName(Class<?> sagaType) {
        return serializer.typeForClass(sagaType).getName();
    }

    /**
     * Sets the Serializer instance to serialize Sagas with. Defaults to the XStream Serializer.
     *
     * @param serializer the Serializer instance to serialize Sagas with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    /**
     * Creates the SQL Schema required to store Sagas and their associations,.
     *
     * @throws SQLException When an error occurs preparing of executing the required statements
     */
    public void createSchema() throws SQLException {
        final Connection connection = connectionProvider.getConnection();
        try {
            sqldef.sql_createTableSagaEntry(connection).executeUpdate();
            sqldef.sql_createTableAssocValueEntry(connection).executeUpdate();
        } finally {
            closeQuietly(connection);
        }
    }

    private static class EntryImpl<S> implements Entry<S> {
        private final Set<AssociationValue> associations;
        private final S loadedSaga;

        public EntryImpl(Set<AssociationValue> associations, S loadedSaga) {
            this.associations = associations;
            this.loadedSaga = loadedSaga;
        }

        @Override
        public TrackingToken trackingToken() {
            return null;
        }

        @Override
        public Set<AssociationValue> associationValues() {
            return associations;
        }

        @Override
        public S saga() {
            return loadedSaga;
        }
    }
}
