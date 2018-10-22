/*
 * Copyright (c) 2010-2018. Axon Framework
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

import javax.persistence.EntityManager;

/**
 * Provides components with an EntityManager to access the persistence mechanism. Depending on the application
 * environment, this may be a single container managed EntityManager, or an application managed instance for one-time
 * use.
 * <p/>
 * Note that the implementation is responsible for keeping track of transaction scope, if necessary. Generally, this is
 * the case when using application-managed EntityManagers.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public interface EntityManagerProvider {

    /**
     * Returns the EntityManager instance to use.
     * <p/>
     * Note that the implementation is responsible for keeping track of transaction scope, if necessary. Generally,
     * this is the case when using application-managed EntityManagers.
     *
     * @return the EntityManager instance to use.
     */
    EntityManager getEntityManager();
}
