package org.axonframework.eventsourcing.eventstore.jpa;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.axonframework.common.AxonConfigurationException;

/**
 * A {@link SQLErrorCodesResolver} with fallback on SQL state.
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class SQLErrorCodesOrStateResolver extends SQLErrorCodesResolver {

	/**
	 * Initializes the SQLErrorCodesResolver using the given list of SQL Codes representing Key Constraint Violations.
	 *
	 * @param duplicateKeyCodes A list of Integer containing SQL Codes representing Key Constraint Violations
	 */
	public SQLErrorCodesOrStateResolver(List<Integer> duplicateKeyCodes) {
		super(duplicateKeyCodes);
	}

	/**
	 * Initialize a SQLErrorCodesResolver, automatically detecting the database name through the given dataSource. The
	 * database product name is used to resolve the database error codes.
	 *
	 * @param dataSource The data source providing the information about the backing database.
	 * @throws java.sql.SQLException      when retrieving the database product name fails
	 * @throws AxonConfigurationException is the dataSource returns an unknown database product name. Use {@link
	 *                                    #SQLErrorCodesOrStateResolver(java.util.Properties, javax.sql.DataSource)} instead.
	 */
	public SQLErrorCodesOrStateResolver(DataSource dataSource) throws SQLException {
		super(dataSource);
	}

	/**
	 * Initialize a SQLErrorCodesResolver, automatically detecting the database name through the given dataSource. The
	 * database product name is used to resolve the database error codes. As an alternative you could set the property
	 * databaseDuplicateKeyCodes
	 *
	 * @param databaseProductName The product name of the database
	 * @throws AxonConfigurationException is the dataSource returns an unknown database product name. Use {@link
	 *                                    #SQLErrorCodesOrStateResolver(java.util.Properties, String)} instead.
	 */
	public SQLErrorCodesOrStateResolver(String databaseProductName) {
		super(databaseProductName);
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
	public SQLErrorCodesOrStateResolver(Properties properties, String databaseProductName) {
		super(properties, databaseProductName);
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
	 * @throws java.sql.SQLException when retrieving the database product name fails
	 */
	public SQLErrorCodesOrStateResolver(Properties properties, DataSource dataSource) throws SQLException {
		super(properties, dataSource);
	}

	@Override
	protected boolean isDuplicateKeyViolation(List<Integer> duplicateKeyCodes, SQLException sqlException) {
		if (super.isDuplicateKeyViolation(duplicateKeyCodes, sqlException)) {
			return true;
		}
		if (sqlException.getSQLState() == null) {
			return false;
		}

		try {
			int sqlState = Integer.parseInt(sqlException.getSQLState());
			return duplicateKeyCodes.contains(sqlState);
		} catch (NumberFormatException ignored) {
		}

		return false;
	}
}
