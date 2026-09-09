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

package org.axonframework.extensions.mongo.eventhandling.saga.repository;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.axonframework.conversion.Converter;
import org.axonframework.modelling.saga.AssociationValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.eq;

/**
 * Java representation of sagas stored in a mongo instance
 *
 * @param <T> the type of saga stored in this entry
 * @author Jettro Coenradie
 * @since 2.0
 */
public class SagaEntry<T> {

    private static final String SAGA_IDENTIFIER = "sagaIdentifier";
    private static final String SERIALIZED_SAGA = "serializedSaga";
    private static final String SAGA_TYPE = "sagaType";
    private static final String ASSOCIATIONS = "associations";
    private static final String ASSOCIATION_KEY = "key";
    private static final String ASSOCIATION_VALUE = "value";

    private final String sagaId;
    private final String sagaType;

    private final byte[] serializedSaga;

    private final AtomicReference<T> saga = new AtomicReference<>();
    private final Set<AssociationValue> associationValues;

    /**
     * Constructs a new SagaEntry for the given {@code saga}. The given saga must be convertible to a byte array. The
     * provided saga is not modified by this operation.
     *
     * @param identifier        The identifier of the saga
     * @param saga              The saga to store
     * @param associationValues The associations of the saga
     * @param converter         The conversion mechanism to convert the Saga to a byte stream
     */
    public SagaEntry(String identifier, T saga, Set<AssociationValue> associationValues, Converter converter) {
        this.sagaId = identifier;
        this.serializedSaga = converter.convert(saga, byte[].class);
        this.sagaType = saga.getClass().getName();
        this.saga.set(saga);
        this.associationValues = new HashSet<>(associationValues);
    }

    /**
     * Initializes a Saga entry using a Document containing the Mongo Document
     *
     * @param dbSaga The mongo Document containing the converted saga
     */
    public SagaEntry(Document dbSaga) {
        this.sagaId = (String) dbSaga.get(SAGA_IDENTIFIER);
        this.serializedSaga = ((Binary) dbSaga.get(SERIALIZED_SAGA)).getData();
        this.sagaType = (String) dbSaga.get(SAGA_TYPE);
        this.associationValues = toAssociationSet(dbSaga);
    }

    /**
     * Returns the Saga instance stored in this entry, converted into the given {@code sagaType}.
     * <p>
     * Takes the target type because a {@link Converter} needs one, where the {@code Serializer} this replaces resolved
     * it from the type name stored alongside the saga. That name is still written, so a reader outside this module can
     * tell what a document holds, but conversion goes by what the caller asked for.
     *
     * @param converter The converter to convert the Saga with
     * @param sagaType  The class to convert the stored saga into
     * @return the Saga instance stored in this entry
     */
    public T getSaga(Converter converter, Class<T> sagaType) {
        return saga.updateAndGet(current -> {
            if (current != null) {
                return current;
            } else {
                return converter.convert(serializedSaga, sagaType);
            }
        });
    }

    /**
     * Get the identifier of this Saga.
     *
     * @return the saga identifier
     */
    public String getSagaId() {
        return sagaId;
    }

    /**
     * Get a set of all the Saga's associations.
     *
     * @return association values of this Saga
     */
    public Set<AssociationValue> getAssociationValues() {
        return associationValues;
    }

    /**
     * Returns the Mongo Document representing the Saga provided in this entry.
     *
     * @return the Mongo Document representing the Saga provided in this entry
     */
    public Document asDocument() {
        return new Document(SAGA_TYPE, sagaType).append(SAGA_IDENTIFIER, sagaId).append(SERIALIZED_SAGA, serializedSaga)
                                                .append(ASSOCIATIONS, toDBList(associationValues));
    }

    @SuppressWarnings("unchecked")
    private Set<AssociationValue> toAssociationSet(Document dbSaga) {
        Set<AssociationValue> values = new HashSet<>();
        List<Document> list = (List<Document>) dbSaga.get(ASSOCIATIONS);
        if (list != null) {
            values.addAll(list.stream().map(item -> new AssociationValue((String) item.get(ASSOCIATION_KEY),
                                                                        (String) item.get(ASSOCIATION_VALUE)))
                              .collect(Collectors.toList()));
        }
        return values;
    }

    private static List<Object> toDBList(Iterable<AssociationValue> associationValues) {
        BasicDBList list = new BasicDBList();
        for (AssociationValue associationValue : associationValues) {
            list.add(new BasicDBObject(ASSOCIATION_KEY, associationValue.getKey())
                             .append(ASSOCIATION_VALUE, associationValue.getValue()));
        }
        return list;
    }

    /**
     * Returns the Mongo Query to find a Saga based on its identifier.
     *
     * @param identifier The identifier of the saga to find
     * @return the Query (as Bson) to find a Saga in a Mongo Database
     */
    public static Bson queryByIdentifier(String identifier) {
        return eq(SAGA_IDENTIFIER, identifier);
    }
}
