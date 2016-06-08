/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.io.IOUtils;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityExistsException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * SQLErrorCodesResolver is an implementation of PersistenceExceptionResolver used to resolve sql error codes to see if
 * it is an duplicate key constraint violation.
 * <p/>
 * SQL Code configuration is available for the following database engines, which may be identifier automatically via
 * the data source: <ul>
 * <li>HSQL DB</li>
 * <li>MySQL</li>
 * <li>Apache Derby</li>
 * <li>DB2</li>
 * <li>H2</li>
 * <li>Informix Dynamic Server</li>
 * <li>MS SQL Server</li>
 * <li>Oracle</li>
 * <li>PostgreSQL</li>
 * <li>Sybase</li>
 * </ul>
 *
 * @author Martin Tilma
 * @author Allard Buijze
 * @since 0.7
 */
public class SQLErrorCodesResolver implements PersistenceExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(SQLErrorCodesResolver.class);
    private static final String SQL_ERROR_CODES_PROPERTIES = "SQLErrorCode.properties";
    private static final String PROPERTY_NAME_SUFFIX = ".duplicateKeyCodes";
    private static final String LIST_SEPARATOR = ",";

    private List<Integer> duplicateKeyCodes = Collections.emptyList();

    /**
     * Initializes the SQLErrorCodesResolver using the given list of SQL Codes representing Key Constraint Violations.
     *
     * @param duplicateKeyCodes A list of Integer containing SQL Codes representing Key Constraint Violations
     */
    public SQLErrorCodesResolver(List<Integer> duplicateKeyCodes) {
        this.duplicateKeyCodes = duplicateKeyCodes;
    }

    /**
     * Initialize a SQLErrorCodesResolver, automatically detecting the database name through the given dataSource. The
     * database product name is used to resolve the database error codes.
     *
     * @param dataSource The data source providing the information about the backing database.
     * @throws java.sql.SQLException      when retrieving the database product name fails
     * @throws AxonConfigurationException is the dataSource returns an unknown database product name. Use {@link
     *                                    #SQLErrorCodesResolver(java.util.Properties, javax.sql.DataSource)} instead.
     */
    public SQLErrorCodesResolver(DataSource dataSource) throws SQLException {
        Properties properties = loadDefaultPropertyFile();
        initialize(properties, getDatabaseProductNameFromDataSource(dataSource));
    }

    /**
     * Initialize a SQLErrorCodesResolver, automatically detecting the database name through the given dataSource. The
     * database product name is used to resolve the database error codes. As an alternative you could set the property
     * databaseDuplicateKeyCodes
     *
     * @param databaseProductName The product name of the database
     * @throws AxonConfigurationException is the dataSource returns an unknown database product name. Use {@link
     *                                    #SQLErrorCodesResolver(java.util.Properties, String)} instead.
     */
    public SQLErrorCodesResolver(String databaseProductName) {
        initialize(loadDefaultPropertyFile(), databaseProductName);
    }

    /**
     * Initialize a SQLErrorCodesResolver, automatically detecting the database name through the given dataSource. The
     * database product name is used to resolve the database error codes. As an alternative you could set the property
     * databaseDuplicateKeyCodes
     * <p/>
     * The form of the properties is expected to be:<br/>
     * <em><code>databaseName</code></em>.duplicateKeyCodes=<code><em>keyCode</em>[,<em>keyCode</em>]*</code><br/>
     * Where <em><code>databaseName</code></em> is the database product name as returned by the driver, with spaces ('
     * ') replaced by underscore ('_'). The key codes must be a comma separated list of SQL Error code numbers (int).
     *
     * @param properties          the properties defining SQL Error Codes for Duplicate Key violations for different
     *                            databases
     * @param databaseProductName The product name of the database
     */
    public SQLErrorCodesResolver(Properties properties, String databaseProductName) {
        initialize(properties, databaseProductName);
    }

    /**
     * Initialize the SQLErrorCodesResolver with the given <code>properties</code> and use the <code>dataSource</code>
     * to automatically retrieve the database product name.
     * <p/>
     * The form of the properties is expected to be:<br/>
     * <em><code>databaseName</code></em>.duplicateKeyCodes=<code><em>keyCode</em>[,<em>keyCode</em>]*</code><br/>
     * Where <em><code>databaseName</code></em> is the database product name as returned by the driver, with spaces ('
     * ') replaced by underscore ('_'). The key codes must be a comma separated list of SQL Error code numbers (int).
     *
     * @param properties the properties defining SQL Error Codes for Duplicate Key violations for different databases
     * @param dataSource The data source providing the database product name
     * @throws java.sql.SQLException when retrieving the database product name fails
     */
    public SQLErrorCodesResolver(Properties properties, DataSource dataSource) throws SQLException {
        initialize(properties, getDatabaseProductNameFromDataSource(dataSource));
    }

    private void initialize(Properties properties, String databaseProductName) {
        duplicateKeyCodes = loadKeyViolationCodes(databaseProductName, properties);
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public boolean isDuplicateKeyViolation(Exception exception) {
        if (causeIsEntityExistsException(exception)) {
            return true;
        }
        SQLException sqlException = findSQLException(exception);
        boolean isDuplicateKey = false;
        if (sqlException != null) {
            isDuplicateKey = duplicateKeyCodes.contains(sqlException.getErrorCode());
        }
        return isDuplicateKey;
    }

    private boolean causeIsEntityExistsException(Throwable exception) {
        return exception instanceof EntityExistsException
                || (exception.getCause() != null && causeIsEntityExistsException(exception.getCause()));
    }

    private SQLException findSQLException(Throwable exception) {
        SQLException sqlException = null;
        while (sqlException == null && exception != null) {
            if (exception instanceof SQLException) {
                sqlException = (SQLException) exception;
            } else {
                exception = exception.getCause();
            }
        }

        return sqlException;
    }

    private String getDatabaseProductNameFromDataSource(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return connection.getMetaData().getDatabaseProductName();
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                // we did our best.
                logger.warn("An error occurred while trying to close the database connection. Ignoring...", e);
            }
        }
    }

    private List<Integer> loadKeyViolationCodes(String databaseProductName, Properties properties) {
        String key = databaseProductName.replaceAll(" ", "_") + PROPERTY_NAME_SUFFIX;
        String property = properties.getProperty(key);

        List<Integer> keyCodes = new ArrayList<>();

        if (property == null) {
            throw new AxonConfigurationException(String.format(
                    "The database product name '%s' is unknown. No SQLCode configuration is known for that database.",
                    databaseProductName));
        }
        String[] codes = property.split(LIST_SEPARATOR);
        for (String code : codes) {
            keyCodes.add(Integer.valueOf(code));
        }

        return keyCodes;
    }

    private Properties loadDefaultPropertyFile() {
        Properties properties = new Properties();
        InputStream resources = null;
        try {
            resources = SQLErrorCodesResolver.class.getResourceAsStream(SQL_ERROR_CODES_PROPERTIES);
            properties.load(resources);
        } catch (IOException e) {
            throw new AxonConfigurationException("Unable to read from a file that should be ", e);
        } finally {
            IOUtils.closeQuietly(resources);
        }
        return properties;
    }
}
