/*
 * Copyright (c) 2010-2012. Axon Framework
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

import org.axonframework.common.io.JdbcUtils;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.saga.repository.jpa.SagaEntry;
import org.axonframework.saga.repository.jpa.SerializedSaga;
import org.axonframework.serializer.JavaSerializer;
import org.axonframework.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.axonframework.common.io.JdbcUtils.*;

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
    private final DataSource dataSource;

    private final GenricSagaSqlSchema sqldef;


    /**
     * Initializes a Saga Repository.
     *
     * @param dataSource The datasource to use
     * @param sqldef The sql schema&command definition
     */
    public JdbcSagaRepository(DataSource dataSource, GenricSagaSqlSchema sqldef) {
        this.dataSource = dataSource;
        this.sqldef = sqldef;
        serializer = new JavaSerializer();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Saga load(String sagaId) {
        PreparedStatement ps = sqldef.sql_loadSaga(conn(), sagaId);
        ResultSet resultSet = executeQuery(ps);

        try {
            List<SerializedSaga> serializedSagaList = JdbcUtils.createList(resultSet, getFactory());
            // Jpa uses setMaxResults==1. This seems a bit nondeterministic wrt jpa if there's ever more than 1...?
            if (serializedSagaList == null || serializedSagaList.isEmpty()) {
                return null;
            }
            SerializedSaga serializedSaga = serializedSagaList.get(0);
            Saga loadedSaga = serializer.deserialize(serializedSaga);
            if (injector != null) {
                injector.injectResources(loadedSaga);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Loaded saga id [{}] of type [{}]", sagaId, loadedSaga.getClass().getName());
            }
            return loadedSaga;
        } finally {
            JdbcUtils.closeAllQuietly(resultSet);
        }

    }

    private ResultSetParser<SerializedSaga> getFactory() {
        return new ResultSetParser<SerializedSaga>() {
            @Override
            public SerializedSaga createItem(ResultSet rs) throws SQLException {
                return new SerializedSaga(rs.getBytes(1), rs.getString(2), rs.getString(3));
            }
        };
    }


    @SuppressWarnings({"unchecked"})
    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        PreparedStatement sql = sqldef.sql_removeAssocValue(conn(), associationValue.getKey(), associationValue.getValue(), sagaType, sagaIdentifier);
        int updateCount = JdbcUtils.executeUpdate(sql);
        if (updateCount == 0 && logger.isWarnEnabled()) {
            logger.warn("Wanted to remove association value, but it was already gone: sagaId= {}, key={}, value={}",
                    new Object[]{sagaIdentifier,
                            associationValue.getKey(),
                            associationValue.getValue()});
        }
    }

    @Override
    protected String typeOf(Class<? extends Saga> sagaClass) {
        return serializer.typeForClass(sagaClass).getName();
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        executeUpdate(sqldef.sql_storeAssocValue(conn(), associationValue.getKey(), associationValue.getValue(), sagaType, sagaIdentifier));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<String> findAssociatedSagaIdentifiers(Class<? extends Saga> type, AssociationValue associationValue) {
        ResultSet resultSet = executeQuery(sqldef.sql_findAssocSagaIdentifiers(conn(), associationValue.getKey(), associationValue.getValue(), typeOf(type)));

        final List<String> list = JdbcUtils.createList(resultSet, new ResultSetParser<String>() {
            @Override
            public String createItem(ResultSet rs) throws SQLException {
                return rs.getString(1);
            }
        });
        return new TreeSet<String>(list);
    }

    @Override
    protected void deleteSaga(Saga saga) {
        executeUpdate(sqldef.sql_deleteAssocValueEntry(conn(), saga.getSagaIdentifier()));
        executeUpdate(sqldef.sql_deleteSagaEntry(conn(),  saga.getSagaIdentifier()));
    }

    @Override
    protected void updateSaga(Saga saga) {
        SagaEntry entry = new SagaEntry(saga, serializer);
        if (logger.isDebugEnabled()) {
            logger.debug("Updating saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
                    Charset.forName("UTF-8")));
        }

        int updateCount = executeUpdate(sqldef.sql_updateSaga(conn(), entry.getSerializedSaga(), entry.getRevision(), entry.getSagaId(), entry.getSagaType()));

        if (updateCount == 0) {
            logger.warn("Expected to be able to update a Saga instance, but no rows were found. Inserting instead.");
            storeSaga(saga);
        }
    }

    @Override
    protected void storeSaga(Saga saga) {
        SagaEntry entry = new SagaEntry(saga, serializer);
        executeUpdate(sqldef.sql_storeSaga(conn(), entry.getSagaId(), entry.getRevision(), entry.getSagaType(), entry.getSerializedSaga()));
        if (logger.isDebugEnabled()) {
            logger.debug("Storing saga id {} as {}", saga.getSagaIdentifier(), new String(entry.getSerializedSaga(),
                    Charset.forName("UTF-8")));
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
        execute(sqldef.sql_createTableSagaEntry(conn()));
        execute(sqldef.sql_createTableAssocValueEntry(conn()));
    }

    public void deleteAllEventData() throws SQLException {
        execute(sqldef.sql_deleteAllAssocValueEntries(conn()));
        execute(sqldef.sql_deleteAllSagaEntries(conn()));
    }

    private Connection conn(){
        return getConnection(dataSource);
    }

}
