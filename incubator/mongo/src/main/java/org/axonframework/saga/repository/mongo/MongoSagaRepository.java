package org.axonframework.saga.repository.mongo;

import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.NoSuchSagaException;
import org.axonframework.saga.ResourceInjector;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.AbstractSagaRepository;
import org.axonframework.saga.repository.JavaSagaSerializer;
import org.axonframework.saga.repository.SagaSerializer;

import javax.annotation.Resource;
import java.util.Set;

/**
 * @author Jettro Coenradie
 */
public class MongoSagaRepository extends AbstractSagaRepository {

    private MongoTemplate mongoTemplate;
    private SagaSerializer serializer;
    private ResourceInjector injector;

    private volatile boolean initialized = false;

    public MongoSagaRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.serializer = new JavaSagaSerializer();
    }

    @Override
    public <T extends Saga> Set<T> find(Class<T> type, Set<AssociationValue> associationValues) {
        if (!initialized) {
            initialize();
        }
        return super.find(type, associationValues);
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
        super.commit(saga);
    }

    private synchronized void initialize() {
        if (!initialized) {
            DBCursor dbCursor = mongoTemplate.associationsCollection().find();
            getAssociationValueMap().clear();
            while (dbCursor.hasNext()) {
                AssociationValueEntry entry = new AssociationValueEntry(dbCursor.next());
                getAssociationValueMap().add(entry.getAssociationValue(), entry.getSagaIdentifier());
            }
            initialized = true;
        }
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
        mongoTemplate.sagaCollection().save(sagaEntry.asDBObject());
    }

    @Override
    protected void storeAssociationValue(AssociationValue associationValue, String sagaIdentifier) {
        AssociationValueEntry associationValueEntry = new AssociationValueEntry(sagaIdentifier, associationValue);
        mongoTemplate.associationsCollection().save(associationValueEntry.asDBObject());
    }

    @Override
    protected void removeAssociationValue(AssociationValue associationValue, String sagaIdentifier) {
        DBObject query = AssociationValueEntry.queryBySagaIdentifierAndAssociationKeyValue(
                sagaIdentifier, associationValue.getKey(), (String) associationValue.getValue());
        mongoTemplate.associationsCollection().findAndRemove(query);
    }

    /**
     * Provide the serializer to use if the default JavaSagaSerializer is not the best solution.
     *
     * @param serializer SagaSerializer to use for sag serialization.
     */
    public void setSerializer(SagaSerializer serializer) {
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
