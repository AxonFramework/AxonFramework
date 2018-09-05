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

package org.axonframework.spring.eventhandling.scheduling.quartz;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                executeCreateSQL();
            }
        });
    }

    private void executeCreateSQL() {
        List<String> statements;
        try {
            String script = IOUtils.toString(sqlResource.getInputStream());
            statements = Arrays.asList(script.split(";"));
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

    private void executeSqlScript(String sqlResourcePath) throws DataAccessException {
        EncodedResource resource =
                new EncodedResource(applicationContext.getResource(sqlResourcePath), "UTF-8");
        List<String> statements = new LinkedList<>();
        try {
            LineNumberReader lnr = new LineNumberReader(resource.getReader());
            String delimiter = ";";
            String script = ScriptUtils.readScript(lnr, "--", delimiter);
            if (!ScriptUtils.containsSqlScriptDelimiters(script, delimiter)) {
                delimiter = "\n";
            }
            ScriptUtils.splitSqlScript(script, delimiter, statements);
            for (String statement : statements) {
                this.entityManager.createNativeQuery(statement).executeUpdate();
            }
        } catch (IOException ex) {
            throw new DataAccessResourceFailureException("Failed to open SQL script '" + sqlResourcePath + "'", ex);
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
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
