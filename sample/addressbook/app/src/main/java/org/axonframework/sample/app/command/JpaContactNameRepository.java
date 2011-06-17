/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.sample.app.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

/**
 * <p>Jpa implementation for the contact claim repository</p>
 *
 * @author Jettro Coenradie
 */
@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class JpaContactNameRepository implements ContactNameRepository {
    private static final Logger logger = LoggerFactory.getLogger(JpaContactNameRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean claimContactName(String contactName) {
        try {
            entityManager.persist(new ClaimedContactName(contactName));
            entityManager.flush();
            return true;
        } catch (RuntimeException e) {
            logger.warn("Unable to claim contact name.", e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancelContactName(String contactName) {
        try {
            ClaimedContactName claimedContactName = entityManager.getReference(ClaimedContactName.class, contactName);
            entityManager.remove(claimedContactName);
        } catch (EntityNotFoundException e) {
            logger.warn("Could not obtain reference to the claimed contact name {}", contactName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean vacantContactName(String contactName) {
        ClaimedContactName claimedContactName = entityManager.find(ClaimedContactName.class, contactName);
        return claimedContactName == null;
    }
}
