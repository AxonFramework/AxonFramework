package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.SagaSerializer;

/**
 * <p>Java representation of sagas stored in a mongo instance.</p>
 *
 * @author Jettro Coenradie
 */
public class SagaEntry {
    static final String SAGA_IDENTIFIER = "sagaIdentifier";
    static final String SERIALIZED_SAGA = "serializedSaga";
    private String sagaId;

    private byte[] serializedSaga;

    /**
     * Constructs a new SagaEntry for the given <code>saga</code>. The given saga must be serializable. The provided
     * saga is not modified by this operation.
     *
     * @param saga       The saga to store
     * @param serializer The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(Saga saga, SagaSerializer serializer) {
        this.sagaId = saga.getSagaIdentifier();
        this.serializedSaga = serializer.serialize(saga);
    }

    public SagaEntry(DBObject dbSaga) {
        this.sagaId = (String) dbSaga.get(SAGA_IDENTIFIER);
        this.serializedSaga = (byte[]) dbSaga.get(SERIALIZED_SAGA);
    }

    /**
     * Returns the Saga instance stored in this entry.
     *
     * @param serializer The serializer to decode the Saga
     * @return the Saga instance stored in this entry
     */
    public Saga getSaga(SagaSerializer serializer) {
        return serializer.deserialize(serializedSaga);
    }

    /**
     * Returns the Identifier of the Saga stored in this entry.
     *
     * @return the Identifier of the Saga stored in this entry
     */
    public String getSagaId() {
        return sagaId;
    }

    public DBObject asDBObject() {
        return BasicDBObjectBuilder.start()
                .add(SAGA_IDENTIFIER, sagaId)
                .add(SERIALIZED_SAGA, serializedSaga)
                .get();
    }

    public static DBObject queryByIdentifier(String identifier) {
        return BasicDBObjectBuilder.start()
                .add(SAGA_IDENTIFIER, identifier)
                .get();
    }
}
