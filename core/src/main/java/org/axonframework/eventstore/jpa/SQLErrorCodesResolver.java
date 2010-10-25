package org.axonframework.eventstore.jpa;

import javax.persistence.PersistenceException;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * SQLErrorCodesResolver is an implementation of PersistenceExceptionResolver used to resolve sql error codes to see if
 * it is an duplicate key constraint violation
 *
 * @author Martin Tilma
 * @since 0.7
 *
 */
public class SQLErrorCodesResolver implements PersistenceExceptionResolver {

    private final static String SQL_ERROR_CODES_PROPERTIES = "SQLErrorCode.properties";
    private final static String PROPERTY_NAME_SUFFIX = ".duplicateKeyCodes";
    private static final String LIST_SEPARATOR = ",";

    private List<Integer> duplicateKeyCodes = Collections.emptyList();

    public SQLErrorCodesResolver() {
    }

    public boolean isDuplicateKey(PersistenceException persistenceException) {
        SQLException sqlException = findSQLException(persistenceException);
        boolean isDuplicateKey = false;
        if (sqlException != null) {
            isDuplicateKey = duplicateKeyCodes.contains(sqlException.getErrorCode());
        }
        return isDuplicateKey;
    }

    private SQLException findSQLException(PersistenceException persistenceException) {

        SQLException sqlException = null;
        Throwable cause = persistenceException.getCause();
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
     * @param dataSource
     */
    public void setDataSource(DataSource dataSource) {
        loadDuplicateKeyCodes(dataSource);

    }

    private void loadDuplicateKeyCodes(DataSource dataSource) {
        String databaseProductName = getDatabaseProductNameFromDataSource(dataSource);
        duplicateKeyCodes = loadFromPropertiesFile(databaseProductName);
    }

    private String getDatabaseProductNameFromDataSource(DataSource dataSource) {
        try {
            return dataSource.getConnection().getMetaData().getDatabaseProductName();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not get the database product name because getting the connection gives an error: " + e
                            .getMessage(),
                    e);
        }
    }

    private List<Integer> loadFromPropertiesFile(String databaseProductName) {

        Properties properties = loadPropertyFile();

        String key = databaseProductName.replaceAll(" ", "_") + PROPERTY_NAME_SUFFIX;
        String property = properties.getProperty(key);

        List<Integer> duplicateKeyCodes = new ArrayList<Integer>();

        if (property != null) {
            String[] codes = property.split(LIST_SEPARATOR);
            for (int i = 0; i < codes.length; i++) {
                Integer code = Integer.valueOf(codes[i]);
                duplicateKeyCodes.add(code);
            }
        }

        return duplicateKeyCodes;
    }

    private Properties loadPropertyFile() {
        Properties properties = new Properties();
        try {
            properties.load(SQLErrorCodesResolver.class.getResourceAsStream(SQL_ERROR_CODES_PROPERTIES));
        } catch (IOException e) {
            throw new RuntimeException("Could not load properties file " + SQL_ERROR_CODES_PROPERTIES, e);
        }
        return properties;
    }

    /**
     * Set the duplicate key codes, use this instead of the setDataStore if you're using a database that isn't listed in
     * the <code>SQL_ERROR_CODES_PROPERTIES</code> files
     *
     * @param duplicateKeyCodes
     */
    public void setDuplicateKeyCodes(List<Integer> duplicateKeyCodes) {
        this.duplicateKeyCodes = duplicateKeyCodes;
    }
}
