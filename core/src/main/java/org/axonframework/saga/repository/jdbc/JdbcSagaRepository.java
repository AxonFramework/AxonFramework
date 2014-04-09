/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.saga.repository.jdbc;

import org.axonframework.common.jdbc.ConnectionProvider;
import org.axonframework.common.jdbc.DataSourceConnectionProvider;
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.SagaStorageException;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.saga.repository.jpa.SagaEntry;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;

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
public class JdbcSagaRepository extends AbstractSagaRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSagaRepository.class);

    private ResourceInjector injector;
    private Serializer serializer;
    private final ConnectionProvider connectionProvider;

    private final SagaSqlSchema sqldef;

    /**
     * Initializes a Saga Repository, using given code>connectionProvider</code> to obtain connections to the database,
     * using a Generic SQL Schema.
     *
     * @param connectionProvider The data source to obtain connections from
     */
    public JdbcSagaRepository(ConnectionProvider connectionProvider) {
        this(connectionProvider, new GenericSagaSqlSchema());
    }

    /**
     * Initializes a Saga Repository, using given <code>dataSource</code> to obtain connections to the database, and
     * given <code>sqldef</code> to exectute SQL statements.
     *
     * @param dataSource The data source to obtain connections from
     * @param sqldef     The definition of SQL operations to execute
     */
    public JdbcSagaRepository(DataSource dataSource, SagaSqlSchema sqldef) {
        this(new UnitOfWorkAwareConnectionProviderWrapper(new DataSourceConnectionProvider(dataSource)), sqldef);
    }

    /**
     * Initializes a Saga Repository, using given <code>connectionProvider</code> to obtain connections to the
     * database, and given <code>sqldef</code> to exectute SQL statements.
     *
     * @param connectionProvider The provider to obtain connections from
     * @param sqldef             The definition of SQL operations to execute
     */
    public JdbcSagaRepository(ConnectionProvider connectionProvider, SagaSqlSchema sqldef) {
        this(connectionProvider, sqldef, new XStreamSerializer());
    }

    /**
     * Initializes a Saga Repository, using given <code>connectionProvider</code> to obtain connections to the
     * database, and given <code>sqldef</code> to exectute SQL statements and <code>serializer</code> to serialize
     * Sagas.
     *
     * @param connectionProvider The provider to obtain connections from
     * @param sqldef             The definition of SQL operations to execute
     */
    public JdbcSagaRepository(ConnectionProvider connectionProvider,
                              SagaSqlSchema sqldef, Serializer serializer) {
        this.connectionProvider = connectionProvider;
        this.sqldef = sqldef;
        this.serializer = serializer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Saga load(String sagaId) {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_loadSaga(conn, sagaId);
            resultSet = statement.executeQuery();

            SerializedObject<?> serializedSaga = null;
            if (resultSet.next()) {
                serializedSaga = sqldef.readSerializedSaga(resultSet);
            }
            if (serializedSaga == null) {
                return null;
            }
            Saga loadedSaga = serializer.deserialize(serializedSaga);
            if (injector != null) {
                injector.injectResources(loadedSaga);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Loaded saga id [{}] of type [{}]", sagaId, loadedSaga.getClass().getName());
            }
            return loadedSaga;
        } catch (SQLException e) {
            throw new SagaStorageException("Exception while loading a Saga", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(resultSet);
            closeQuietly(conn);
        }
    }


    @SuppressWarnings({"unchecked"})
    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            PreparedStatement preparedStatement = sqldef.sql_removeAssocValue(conn,
                                                                              associationValue.getKey(),
                                                                              associationValue.getValue(),
                                                                              sagaType,
                                                                              sagaIdentifier);
            int updateCount = preparedStatement.executeUpdate();

            if (updateCount == 0 && logger.isWarnEnabled()) {
                logger.warn("Wanted to remove association value, but it was already gone: sagaId= {}, key={}, value={}",
                            sagaIdentifier,
                            associationValue.getKey(),
                            associationValue.getValue());
            }
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to remove an AssociationValue", e);
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    protected String typeOf(Class<? extends Saga> sagaClass) {
        return serializer.typeForClass(sagaClass).getName();
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        PreparedStatement statement = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_storeAssocValue(conn,
                                                   associationValue.getKey(),
                                                   associationValue.getValue(),
                                                   sagaType,
                                                   sagaIdentifier);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SagaStorageException("Exception while storing an association value", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(conn);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<String> findAssociatedSagaIdentifiers(Class<? extends Saga> type, AssociationValue associationValue) {
        ResultSet resultSet = null;
        PreparedStatement statement = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_findAssocSagaIdentifiers(conn, associationValue.getKey(),
                                                            associationValue.getValue(), typeOf(type));
            resultSet = statement.executeQuery();
            Set<String> result = new TreeSet<String>();
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
    protected void deleteSaga(Saga saga) {
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        Connection conn = null;
        try {
            conn = connectionProvider.getConnection();
            statement1 = sqldef.sql_deleteAssociationEntries(conn, saga.getSagaIdentifier());
            statement2 = sqldef.sql_deleteSagaEntry(conn, saga.getSagaIdentifier());
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
    protected void updateSaga(Saga saga) {
        SagaEntry entry = new SagaEntry(saga, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Updating saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
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
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to update a saga", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(conn);
        }

        if (updateCount == 0) {
            logger.warn("Expected to be able to update a Saga instance, but no rows were found. Inserting instead.");
            storeSaga(saga);
        }
    }

    @Override
    protected void storeSaga(Saga saga) {
        SagaEntry entry = new SagaEntry(saga, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Storing saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
                                                                                          Charset.forName("UTF-8")));
        }
        Connection conn = null;
        PreparedStatement statement = null;
        try {
            conn = connectionProvider.getConnection();
            statement = sqldef.sql_storeSaga(conn, entry.getSagaId(), entry.getRevision(), entry.getSagaType(),
                                             entry.getSerializedSaga());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new SagaStorageException("Exception occurred while attempting to store a Saga Entry", e);
        } finally {
            closeQuietly(statement);
            closeQuietly(conn);
        }
    }

    /**
     * Sets the ResourceInjector to use to inject Saga instances with any (temporary) resources they might need. These
     * are typically the resources that could not be persisted with the Saga.
     *
     * @param resourceInjector The resource injector
     */
    public void setResourceInjector(ResourceInjector resourceInjector) {
        this.injector = resourceInjector;
    }

    /**
     * Sets the Serializer instance to serialize Sagas with. Defaults to the XStream Serializer.
     *
     * @param serializer the Serializer instance to serialize Sagas with
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    public void createSchema() throws SQLException {
        final Connection connection = connectionProvider.getConnection();
        try {
            sqldef.sql_createTableSagaEntry(connection).executeUpdate();
            sqldef.sql_createTableAssocValueEntry(connection).executeUpdate();
        } finally {
            closeQuietly(connection);
        }
    }
}
