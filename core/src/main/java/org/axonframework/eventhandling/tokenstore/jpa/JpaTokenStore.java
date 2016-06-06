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
 * @author Rene de Waele
 */
public class JpaTokenStore implements TokenStore {

    private final EntityManagerProvider entityManagerProvider;
    private final Serializer serializer;

    public JpaTokenStore(EntityManagerProvider entityManagerProvider, Serializer serializer) {
        this.entityManagerProvider = entityManagerProvider;
        this.serializer = serializer;
    }

    @Override
    public void storeToken(String processName, int segment, TrackingToken token) {
        EntityManager entityManager = entityManagerProvider.getEntityManager();
        AbstractTokenEntry<?> entity = createEntity(processName, segment, token, serializer);
        int count = entityManager.createQuery("UPDATE " + tokenEntryName() + " SET token = :token " +
                                                      "WHERE processName = :processName AND segment = :segment")
                .setParameter("token", entity.getToken().getData()).setParameter("process", processName)
                .setParameter("segment", segment).executeUpdate();
        if (count == 0) {
            entityManager.persist(entity);
        }
        entityManager.flush();
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

    protected String tokenEntryName() {
        return TokenEntry.class.getSimpleName();
    }

    protected AbstractTokenEntry<?> createEntity(String processName, int segment, TrackingToken token,
                                                 Serializer serializer) {
        return new TokenEntry(processName, segment, token, Instant.now(), serializer);
    }
}
