/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.util.jpa;

import javax.persistence.EntityManager;

/**
 * Simple implementation of the EntityManagerProvider that returns the EntityManager instance provided at construction
 * time.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class SimpleEntityManagerProvider implements EntityManagerProvider {

    private final EntityManager entityManager;

    /**
     * Initializes the SimpleEntityManagerProvider to return the given <code>entityManager</code> each time {@link
     * #getEntityManager()} is called.
     *
     * @param entityManager the entity manager for the EventStore to use.
     */
    public SimpleEntityManagerProvider(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }
}
