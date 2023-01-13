/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing.eventstore.legacyjpa;

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

import static org.axonframework.common.ExceptionUtils.findException;

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
 * <p>
 * This implementation will attempt to locate these error codes in the {@link SQLException#getErrorCode()} and
 * {@link SQLException#getSQLState()}, respectively.
 *
 * @author Martin Tilma
 * @author Allard Buijze
 * @since 0.7
 * @deprecated in favor of using {@link org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver} which moved
 * to jakarta.
 */
@Deprecated
public class SQLErrorCodesResolver implements PersistenceExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(SQLErrorCodesResolver.class);
    private static final String SQL_ERROR_CODES_PROPERTIES = "SQLErrorCode.properties";
    private static final String KEY_CODE_SUFFIX = ".duplicateKeyCodes";
    private static final String PATTERN_SUFFIX = ".pattern";
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
     * @throws SQLException               when retrieving the database product name fails
     * @throws AxonConfigurationException is the dataSource returns an unknown database product name. Use {@link
     *                                    #SQLErrorCodesResolver(Properties, DataSource)} instead.
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
     *                                    #SQLErrorCodesResolver(Properties, String)} instead.
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
     * <em>{@code databaseName}</em>.duplicateKeyCodes=<code><em>keyCode</em>[,<em>keyCode</em>]*</code><br/>
     * Where <em>{@code databaseName}</em> is the database product name as returned by the driver, with spaces ('
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
     * Initialize the SQLErrorCodesResolver with the given {@code properties} and use the {@code dataSource}
     * to automatically retrieve the database product name.
     * <p/>
     * The form of the properties is expected to be:<br/>
     * <em>{@code databaseName}</em>.duplicateKeyCodes=<code><em>keyCode</em>[,<em>keyCode</em>]*</code><br/>
     * Where <em>{@code databaseName}</em> is the database product name as returned by the driver, with spaces ('
     * ') replaced by underscore ('_'). The key codes must be a comma separated list of SQL Error code numbers (int).
     *
     * @param properties the properties defining SQL Error Codes for Duplicate Key violations for different databases
     * @param dataSource The data source providing the database product name
     * @throws SQLException when retrieving the database product name fails
     */
    public SQLErrorCodesResolver(Properties properties, DataSource dataSource) throws SQLException {
        initialize(properties, getDatabaseProductNameFromDataSource(dataSource));
    }

    private void initialize(Properties properties, String databaseProductName) {
        duplicateKeyCodes = loadKeyViolationCodes(databaseProductName, properties);
    }

    @Override
    public boolean isDuplicateKeyViolation(Exception exception) {
        return causeIsEntityExistsException(exception) || findException(exception, SQLException.class)
                .map(sqlException -> isDuplicateKeyCode(sqlException, duplicateKeyCodes)).orElse(false);
    }

    /**
     * @param sqlException The exception to locate the error code in
     * @param errorCodes   The error codes indicating duplicate key violations
     * @return {@code true} if the error code of the {@code sqlException} is in the given list of {@code errorCodes}, otherwise
     * {@code false}
     */
    protected boolean isDuplicateKeyCode(SQLException sqlException, List<Integer> errorCodes) {
        if (errorCodes.contains(sqlException.getErrorCode())) {
            return true;
        } else if (sqlException.getSQLState() != null) {
            try {
                return errorCodes.contains(Integer.parseInt(sqlException.getSQLState()));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private boolean causeIsEntityExistsException(Throwable exception) {
        return exception instanceof EntityExistsException ||
                (exception.getCause() != null && causeIsEntityExistsException(exception.getCause()));
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
        String key = databaseProductName.replaceAll(" ", "_") + KEY_CODE_SUFFIX;
        String property = properties.getProperty(key);

        List<Integer> keyCodes = new ArrayList<>();

        if (property == null) {
            for (String k : properties.stringPropertyNames()) {
                if (k.endsWith(PATTERN_SUFFIX)) {
                    String pattern = properties.getProperty(k);
                    if (databaseProductName.matches(pattern)) {
                        property = properties.getProperty(k.substring(0, k.length() - PATTERN_SUFFIX.length()) + KEY_CODE_SUFFIX);
                    }
                }
            }
            if (property == null) {
                throw new AxonConfigurationException(String.format(
                        "The database product name '%s' is unknown. No SQLCode configuration is known for that database.",
                        databaseProductName));
            }
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
