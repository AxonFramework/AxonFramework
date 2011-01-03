/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.jpa;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * SQLErrorCodesResolver is an implementation of PersistenceExceptionResolver used to resolve sql error codes to see if
 * it is an duplicate key constraint violation.
 *
 * @author Martin Tilma
 * @author Allard Buijze
 * @since 0.7
 */
public class SQLErrorCodesResolver implements PersistenceExceptionResolver {

    private static final Logger logger = LoggerFactory.getLogger(SQLErrorCodesResolver.class);
    private final static String SQL_ERROR_CODES_PROPERTIES = "SQLErrorCode.properties";
    private final static String PROPERTY_NAME_SUFFIX = ".duplicateKeyCodes";
    private static final String LIST_SEPARATOR = ",";

    private List<Integer> duplicateKeyCodes = Collections.emptyList();

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public boolean isDuplicateKeyViolation(Exception exception) {
        SQLException sqlException = findSQLException(exception);
        boolean isDuplicateKey = false;
        if (sqlException != null) {
            isDuplicateKey = duplicateKeyCodes.contains(sqlException.getErrorCode());
        }
        return isDuplicateKey;
    }

    private SQLException findSQLException(Exception exception) {
        SQLException sqlException = null;
        Throwable cause = exception.getCause();
        while (sqlException == null && cause != null) {
            if (cause instanceof SQLException) {
                sqlException = (SQLException) cause;
            } else {
                cause = cause.getCause();
            }
        }

        return sqlException;
    }

    /**
     * Set the dataSource which is needed to get the database product name. The database product name is used to resolve
     * the database error codes. As an alternative you could set the property databaseDuplicateKeyCodes
     *
     * @param dataSource The data source providing the information about the backing database.
     * @throws java.io.IOException When an error occurs while reading from the data source
     */
    public void setDataSource(DataSource dataSource) throws IOException {
        loadDuplicateKeyCodes(dataSource);
    }

    private void loadDuplicateKeyCodes(DataSource dataSource) throws IOException {
        String databaseProductName = getDatabaseProductNameFromDataSource(dataSource);
        duplicateKeyCodes = loadFromPropertiesFile(databaseProductName);
    }

    private String getDatabaseProductNameFromDataSource(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not get the database product name. The connection threw an exception: " + e.getMessage(),
                    e);
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

    private List<Integer> loadFromPropertiesFile(String databaseProductName) throws IOException {

        Properties properties = loadPropertyFile();

        String key = databaseProductName.replaceAll(" ", "_") + PROPERTY_NAME_SUFFIX;
        String property = properties.getProperty(key);

        List<Integer> keyCodes = new ArrayList<Integer>();

        if (property != null) {
            String[] codes = property.split(LIST_SEPARATOR);
            for (String code : codes) {
                keyCodes.add(Integer.valueOf(code));
            }
        }

        return keyCodes;
    }

    private Properties loadPropertyFile() throws IOException {
        Properties properties = new Properties();
        InputStream resources = null;
        try {
            resources = SQLErrorCodesResolver.class.getResourceAsStream(SQL_ERROR_CODES_PROPERTIES);
            properties.load(resources);
        } finally {
            IOUtils.closeQuietly(resources);
        }
        return properties;
    }

    /**
     * Set the duplicate key codes, use this instead of the setDataStore if you're using a database that isn't listed in
     * the <code>SQL_ERROR_CODES_PROPERTIES</code> files.
     *
     * @param duplicateKeyCodes A list of error codes that indicate a duplicate key constraint violation
     */
    public void setDuplicateKeyCodes(List<Integer> duplicateKeyCodes) {
        this.duplicateKeyCodes = duplicateKeyCodes;
    }
}
