/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.contextsupport.spring;

import org.axonframework.unitofwork.TransactionManager;
import org.junit.*;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class TransactionManagerFactoryBeanTest {

    @Test
    public void testInjectTransactionManager() throws Exception {
        TransactionManagerFactoryBean bean = new TransactionManagerFactoryBean();
        final TransactionManager transactionManager = mock(TransactionManager.class);
        bean.setTransactionManager(transactionManager);

        assertEquals(transactionManager, bean.getObject());
    }

    @Test
    public void testInjectPlatformTransactionManager() throws Exception {
        TransactionManagerFactoryBean bean = new TransactionManagerFactoryBean();
        final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        bean.setTransactionManager(transactionManager);

        assertNotSame(transactionManager, bean.getObject());
        assertNotNull(bean.getObject());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInjectArbitraryObject() throws Exception {
        TransactionManagerFactoryBean bean = new TransactionManagerFactoryBean();
        bean.setTransactionManager("This is not accepted");
    }
}
