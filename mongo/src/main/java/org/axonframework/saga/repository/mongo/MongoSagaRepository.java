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

package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.common.Assert;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.serializer.JavaSerializer;
import org.axonframework.serializer.Serializer;

import java.util.Set;
import java.util.TreeSet;

/**
 * Implementations of the SagaRepository that stores Sagas and their associations in a Mongo Database. Each Saga and
 * its associations is stored as a single document.
 *
 * @author Jettro Coenradie
 * @author Allard Buijze
 * @since 2.0
 */
public class MongoSagaRepository extends AbstractSagaRepository {

    private final MongoTemplate mongoTemplate;
    private Serializer serializer;
    private ResourceInjector injector;

    /**
     * Initializes the Repository, using given <code>mongoTemplate</code> to access the collections containing the
     * stored Saga instances.
     *
     * @param mongoTemplate the template providing access to the collections
     */
    public MongoSagaRepository(MongoTemplate mongoTemplate) {
        Assert.notNull(mongoTemplate, "mongoTemplate may not be null");
        this.mongoTemplate = mongoTemplate;
        this.serializer = new JavaSerializer();
    }

    @Override
    public Saga load(String sagaIdentifier) {
        DBObject dbSaga = mongoTemplate.sagaCollection().findOne(SagaEntry.queryByIdentifier(sagaIdentifier));
        if (dbSaga == null) {
            return null;
        }
        SagaEntry sagaEntry = new SagaEntry(dbSaga);
        Saga loadedSaga = sagaEntry.getSaga(serializer);
        if (injector != null) {
            injector.injectResources(loadedSaga);
        }
        return loadedSaga;
    }

    @Override
    protected Set<String> findAssociatedSagaIdentifiers(Class<? extends Saga> type, AssociationValue associationValue) {
        final BasicDBObject value = associationValueQuery(type, associationValue);

        DBCursor dbCursor = mongoTemplate.sagaCollection().find(value, new BasicDBObject("sagaIdentifier", 1));
        Set<String> found = new TreeSet<String>();
        while (dbCursor.hasNext()) {
            found.add((String) dbCursor.next().get("sagaIdentifier"));
        }
        return found;
    }

    private BasicDBObject associationValueQuery(Class<? extends Saga> type, AssociationValue associationValue) {
        final BasicDBObject value = new BasicDBObject();
        value.put("sagaType", typeOf(type));

        final BasicDBObject dbAssociation = new BasicDBObject();
        dbAssociation.put("key", associationValue.getKey());
        dbAssociation.put("value", associationValue.getValue());

        value.put("associations", dbAssociation);
        return value;
    }

    @Override
    protected String typeOf(Class<? extends Saga> sagaClass) {
        return serializer.typeForClass(sagaClass).getName();
    }

    @Override
    protected void deleteSaga(Saga saga) {
        mongoTemplate.sagaCollection().findAndRemove(SagaEntry.queryByIdentifier(saga.getSagaIdentifier()));
    }

    @Override
    protected void updateSaga(Saga saga) {
        SagaEntry sagaEntry = new SagaEntry(saga, serializer);
        mongoTemplate.sagaCollection().findAndModify(
                SagaEntry.queryByIdentifier(saga.getSagaIdentifier()),
                sagaEntry.asDBObject());
    }

    @Override
    protected void storeSaga(Saga saga) {
        SagaEntry sagaEntry = new SagaEntry(saga, serializer);
        DBObject sagaObject = sagaEntry.asDBObject();
        mongoTemplate.sagaCollection().save(sagaObject);
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        mongoTemplate.sagaCollection().update(
                new BasicDBObject("sagaIdentifier", sagaIdentifier).append("sagaType", sagaType),
                new BasicDBObject("$push",
                                  new BasicDBObject("associations",
                                                    new BasicDBObject("key", associationValue.getKey())
                                                            .append("value", associationValue.getValue()))));
    }

    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaType, String sagaIdentifier) {
        mongoTemplate.sagaCollection().update(
                new BasicDBObject("sagaIdentifier", sagaIdentifier).append("sagaType", sagaType),
                new BasicDBObject("$pull",
                                  new BasicDBObject("associations",
                                                    new BasicDBObject("key", associationValue.getKey())
                                                            .append("value", associationValue.getValue()))));
    }

    /**
     * Provide the serializer to use if the default JavaSagaSerializer is not the best solution.
     *
     * @param serializer SagaSerializer to use for sag serialization.
     */
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
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
}
