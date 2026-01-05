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

package org.axonframework.common.jpa;

import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.Objects;

/**
 * An implementation of the {@link EntityManagerProvider} that returns a new {@link EntityManager} instance
 * each time using the {@link EntityManagerFactory}.
 *
 * @author John Hendrikx
 * @since 5.0.2
 */
public class FactoryBasedEntityManagerProvider implements EntityManagerProvider {
    private final EntityManagerFactory entityManagerFactory;

    /**
     * Constructs a new instance.
     *
     * @param entityManagerFactory The {@link EntityManagerFactory} to use, cannot be {@code null}.
     */
    public FactoryBasedEntityManagerProvider(@Nonnull EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory");
    }

    @Override
    public EntityManager getEntityManager() {
        return entityManagerFactory.createEntityManager();
    }
}
