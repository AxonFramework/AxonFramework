package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.saga.Saga;
import org.axonframework.serializer.SerializedObject;
import org.axonframework.serializer.Serializer;
import org.axonframework.serializer.SimpleSerializedObject;

/**
 * Java representation of sagas stored in a mongo instance
 *
 * @author Jettro Coenradie
 */
public class SagaEntry {
    static final String SAGA_IDENTIFIER = "sagaIdentifier";
    static final String SERIALIZED_SAGA = "serializedSaga";
    static final String SAGA_TYPE = "sagaType";

    private String sagaId;
    private String sagaType;
    private String revision;

    private byte[] serializedSaga;

    private transient Saga saga;

    /**
     * Constructs a new SagaEntry for the given <code>saga</code>. The given saga must be serializable. The provided
     * saga is not modified by this operation.
     *
     * @param saga       The saga to store
     * @param serializer The serialization mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(Saga saga, Serializer serializer) {
        this.sagaId = saga.getSagaIdentifier();
        SerializedObject<byte[]> serialized = serializer.serialize(saga, byte[].class);
        this.serializedSaga = serialized.getData();
        this.sagaType = serialized.getType().getName();
        this.saga = saga;
    }

    public SagaEntry(DBObject dbSaga) {
        this.sagaId = (String) dbSaga.get(SAGA_IDENTIFIER);
        this.serializedSaga = (byte[]) dbSaga.get(SERIALIZED_SAGA);
        this.sagaType = (String) dbSaga.get(SAGA_TYPE);
    }

    /**
     * Returns the Saga instance stored in this entry.
     *
     * @param serializer The serializer to decode the Saga
     * @return the Saga instance stored in this entry
     */
    public Saga getSaga(Serializer serializer) {
        if (saga != null) {
            return saga;
        }
        return (Saga) serializer.deserialize(new SimpleSerializedObject<byte[]>(serializedSaga, byte[].class,
                                                                                sagaType, revision));
    }

    /**
     * Returns the serialized form of the Saga.
     *
     * @return the serialized form of the Saga
     */
    public byte[] getSerializedSaga() {
        return serializedSaga; //NOSONAR
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
                .add(SAGA_TYPE,sagaType)
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