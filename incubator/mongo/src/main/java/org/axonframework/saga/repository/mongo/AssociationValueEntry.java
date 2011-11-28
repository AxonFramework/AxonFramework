package org.axonframework.saga.repository.mongo;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.SagaStorageException;

import java.io.Serializable;

/**
 * Mongo java representation of an Association Value belonging to a certain saga.
 *
 * @author Jettro Coenradie
 */
public class AssociationValueEntry {
    static final String ASSOCIATION_KEY = "key";
    static final String ASSOCIATION_VALUE = "value";
    static final String ASSOCIATION_SAGA_IDENTIFIER = "sagaIdentifier";
    private String sagaId;
    private String associationKey;
    private String associationValue;

    /**
     * Initialize a new AssociationValueEntry for a saga with given <code>sagaIdentifier</code> and
     * <code>associationValue</code>.
     *
     * @param sagaIdentifier   The identifier of the saga
     * @param associationValue The association value for the saga
     */
    public AssociationValueEntry(String sagaIdentifier, AssociationValue associationValue) {
        if (!Serializable.class.isInstance(associationValue.getValue())) {
            throw new SagaStorageException("Could not persist a saga association, since the value is not serializable");
        }
        this.sagaId = sagaIdentifier;
        this.associationKey = associationValue.getKey();
        this.associationValue = (String) associationValue.getValue();
    }

    public AssociationValueEntry(DBObject dbObject) {
        this.sagaId = (String) dbObject.get(ASSOCIATION_SAGA_IDENTIFIER);
        this.associationKey = (String) dbObject.get(ASSOCIATION_KEY);
        this.associationValue = (String) dbObject.get(ASSOCIATION_VALUE);
    }

    /**
     * Returns the association value contained in this entry.
     *
     * @return the association value contained in this entry
     */
    public AssociationValue getAssociationValue() {
        return new AssociationValue(associationKey, associationValue);
    }

    /**
     * Returns the Saga Identifier contained in this entry.
     *
     * @return the Saga Identifier contained in this entry
     */
    public String getSagaIdentifier() {
        return sagaId;
    }

    public DBObject asDBObject() {
        return BasicDBObjectBuilder.start()
                .add(ASSOCIATION_KEY, associationKey)
                .add(ASSOCIATION_VALUE, associationValue)
                .add(ASSOCIATION_SAGA_IDENTIFIER, sagaId)
                .get();
    }

    public static DBObject queryByKeyAndValue(String key, String value) {
        return BasicDBObjectBuilder.start()
                .add(ASSOCIATION_KEY, key)
                .add(ASSOCIATION_VALUE, value)
                .get();
    }

    public static DBObject queryBySagaIdentifier(String sagaIdentifier) {
        return BasicDBObjectBuilder.start()
                .add(ASSOCIATION_SAGA_IDENTIFIER, sagaIdentifier)
                .get();
    }

    public static DBObject queryBySagaIdentifierAndAssociationKeyValue(String sagaIdentifier, String key, String value) {
        return BasicDBObjectBuilder.start()
                .add(ASSOCIATION_SAGA_IDENTIFIER, sagaIdentifier)
                .add(ASSOCIATION_KEY, key)
                .add(ASSOCIATION_VALUE, value)
                .get();
    }

}
