/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.javax.springboot;

import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.javax.common.jpa.EntityManagerProvider;
import org.axonframework.javax.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.axonframework.javax.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.javax.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JPA auto-configuration
 *
 * @author Sara Pellegrini
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class JpaJavaxAutoConfigurationTest {

    @Autowired
    private EntityManagerProvider entityManagerProvider;
    @Autowired
    private TokenStore tokenStore;
    @Autowired
    private SagaStore<?> sagaStore;
    @Autowired
    private PersistenceExceptionResolver persistenceExceptionResolver;

    @Test
    void contextInitialization() {
        assertTrue(entityManagerProvider instanceof ContainerManagedEntityManagerProvider);
        assertTrue(tokenStore instanceof JpaTokenStore);
        assertTrue(sagaStore instanceof JpaSagaStore);
        assertTrue(persistenceExceptionResolver instanceof SQLErrorCodesResolver);
    }
}
