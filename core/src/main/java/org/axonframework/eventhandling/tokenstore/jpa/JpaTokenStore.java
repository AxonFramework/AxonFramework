/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.tokenstore.jpa;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.serialization.Serializer;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementation of a token store that uses JPA to save and load tokens. This implementation uses {@link TokenEntry}
 * entities.
 *
 * @author Rene de Waele
 */
public class JpaTokenStore implements TokenStore {

    private final EntityManagerProvider entityManagerProvider;
    private final Serializer serializer;

    /**
     * Initializes a token store with given {@code entityManagerProvider} and {@code serializer}.
     *
     * @param entityManagerProvider The provider of the entity manager
     * @param serializer            The serializer used to serialize tokens
     */
    public JpaTokenStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
        this.entityManagerProvider = entityManagerProvider;
        this.serializer = serializer;
    }

    @Override
    public void storeToken(TrackingToken token, String processName, int segment) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        Object entity = createEntity(token, processName, segment, serializer);
        entityManager.merge(entity);
    }

    @Override
    public TrackingToken fetchToken(String processName, int segment) {
        Optional<SerializedToken> serializedToken = entityManagerProvider.getEntityManager().createQuery(
                "SELECT NEW org.axonframework.eventhandling.tokenstore.jpa.SerializedToken(token, tokenType) " +
                        "FROM " + tokenEntryName() + " WHERE processName = :processName AND segment = :segment",
                SerializedToken.class).setParameter("processName", processName).setParameter("segment", segment)
                .setMaxResults(1).getResultList().stream().findFirst();
        return serializedToken.map(token -> token.getToken(serializer)).orElse(null);
    }

    /**
     * Returns the name of the JPA entity used by the store. Override this when storing token entries that differ from
     * {@link TokenEntry}.
     *
     * @return The entity name
     */
    protected String tokenEntryName() {
        return TokenEntry.class.getSimpleName();
    }

    /**
     * Returns a new token entity to add to the store. Use the given {@code serializer} to serialize the given {@code
     * token}.
     *
     * @param token The tracking token to store
     * @param processName The name of the process to which this token belongs
     * @param segment The segment index of the process
     * @param serializer The serializer to use on the token
     * @return a new entity for the store. This returns a {@link TokenEntry} by default.
     */
    protected Object createEntity(TrackingToken token, String processName, int segment,
                                                 Serializer serializer) {
        return new TokenEntry(processName, segment, token, Instant.now(), serializer);
    }
}
