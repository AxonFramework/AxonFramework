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

package org.axonframework.spring.eventhandling.scheduling.quartz;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * @author Allard Buijze
 */
public class QuartzTableMaker implements ApplicationContextAware {

    @PersistenceContext
    private EntityManager entityManager;
    private ApplicationContext applicationContext;
    private Resource sqlResource;
    private PlatformTransactionManager transactionManager;

    @Transactional
    @PostConstruct
    public void createTables() {
        new TransactionTemplate(transactionManager).execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(@Nonnull TransactionStatus status) {
                executeCreateSQL();
            }
        });
    }

    private void executeCreateSQL() {
        try {
            String script = IOUtils.toString(sqlResource.getInputStream(), Charset.defaultCharset());
            List<String> statements = Arrays.asList(script.split(";"));
            for (String statement : statements) {
                while (statement.trim().startsWith("#")) {
                    statement = statement.trim().split("\n", 2)[1];
                }
                if (statement.trim().length() > 0) {
                    this.entityManager.createNativeQuery(statement.trim()).executeUpdate();
                }
            }
        } catch (IOException ex) {
            throw new DataAccessResourceFailureException("Failed to open SQL script '" + sqlResource + "'", ex);
        }
    }

    public void setSqlResource(Resource sqlResource) {
        this.sqlResource = sqlResource;
    }

    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
