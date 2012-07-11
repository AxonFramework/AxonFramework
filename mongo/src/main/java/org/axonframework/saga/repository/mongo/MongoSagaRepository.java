package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.NoSuchSagaException;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.serializer.JavaSerializer;
import org.axonframework.serializer.Serializer;

import java.util.List;
import java.util.Set;
import javax.annotation.Resource;

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

    private volatile boolean initialized = false;

    /**
     * Initializes the Repository, using given <code>mongoTemplate</code> to access the collections containing the
     * stored Saga instances.
     *
     * @param mongoTemplate the template providing access to the collections
     */
    public MongoSagaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.serializer = new JavaSerializer();
    }

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, AssociationValue associationValue) {
        if (!initialized) {
            initialize();
        }
        return super.find(type, associationValue);
    }

    @Override
    public void add(Saga saga) {
        if (!initialized) {
            initialize();
        }
        super.add(saga);
    }

    @Override
    public void commit(Saga saga) {
        if (!initialized) {
            initialize();
        }
        super.commit(saga);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @SuppressWarnings("unchecked")
    private synchronized void initialize() {
        if (!initialized) {
            DBCursor dbCursor = mongoTemplate.sagaCollection().find(null,
                                                                    new BasicDBObject("associations", 1)
                                                                            .append("sagaIdentifier", 1)
                                                                            .append("sagaType", 1));
            getAssociationValueMap().clear();
            while (dbCursor.hasNext()) {
                DBObject next = dbCursor.next();
                List<DBObject> associations = (List<DBObject>) next.get("associations");
                if (associations != null) {
                    String sagaId = (String) next.get("sagaIdentifier");
                    String sagaType = (String) next.get("sagaType");
                    for (DBObject association : associations) {
                        getAssociationValueMap().add(new AssociationValue((String) association.get("key"),
                                                                          (String) association.get("value")),
                                                     sagaType, sagaId);
                    }
                }
            }
            initialized = true;
        }
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
    protected <T extends Saga> T loadSaga(Class<T> type, String sagaIdentifier) {
        DBObject dbSaga = mongoTemplate.sagaCollection().findOne(SagaEntry.queryByIdentifier(sagaIdentifier));
        if (dbSaga == null) {
            throw new NoSuchSagaException(type, sagaIdentifier);
        }
        SagaEntry sagaEntry = new SagaEntry(dbSaga);
        Saga loadedSaga = sagaEntry.getSaga(serializer);
        if (!type.isInstance(loadedSaga)) {
            return null;
        }
        T storedSaga = type.cast(loadedSaga);
        if (injector != null) {
            injector.injectResources(storedSaga);
        }
        return storedSaga;
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
    @Resource
    public void setResourceInjector(ResourceInjector resourceInjector) {
        this.injector = resourceInjector;
    }
}