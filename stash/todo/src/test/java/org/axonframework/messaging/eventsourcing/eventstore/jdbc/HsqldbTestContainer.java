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

package org.axonframework.messaging.eventsourcing.eventstore.jdbc;

import org.hsqldb.jdbc.JDBCDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.Map;

/**
 * Implements a HSQL DB Test Container.
 */
public class HsqldbTestContainer extends JdbcDatabaseContainer<HsqldbTestContainer> {

    public static final String TAG = "2.3.4";
    public static final String IMAGE = "datagrip/hsqldb:" + TAG;
    public static final Integer PORT = 9001;

    private String databaseName = "test";
    private String username = "sa";
    private String password = "";

    public HsqldbTestContainer() {
        this(IMAGE);
    }

    public HsqldbTestContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(PORT);
        withEnv("HSQLDB_DATABASE_ALIAS", databaseName);
        withEnv("HSQLDB_DATABASE_NAME", databaseName);
        withEnv("HSQLDB_USER", username);
        withEnv("HSQLDB_PASSWORD", password);
        withReuse(false); // discard data on stop
        // Optionally mount a tmpfs (true ephemeral, in-memory FS at container level)
        withTmpFs(Map.of("/opt/hsqldb-data", "rw"));
    }

    @Override
    protected void configure() {
        // H2 doesn’t need a lot of config by default
    }

    @Override
    public String getDriverClassName() {
        return "org.hsqldb.jdbc.JDBCDriver";
    }

    @Override
    public String getJdbcUrl() {
        return String.format("jdbc:hsqldb:hsql://%s:%d/%s", getHost(), getMappedPort(PORT), getDatabaseName());
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS";
    }

    public HsqldbTestContainer withDatabaseName(String dbName) {
        this.databaseName = dbName;
        return this;
    }

    public HsqldbTestContainer withUsername(String username) {
        this.username = username;
        return this;
    }

    public HsqldbTestContainer withPassword(String password) {
        this.password = password;
        return this;
    }

    public JDBCDataSource getDataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl(getJdbcUrl());
        dataSource.setUser(getUsername());
        dataSource.setPassword(getPassword());
        return dataSource;
    }
}