package org.axonframework.eventsourcing.eventstore.jpa;

import static org.junit.Assert.*;

import javax.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.Properties;

import org.junit.Test;

/**
 * Created on 10/02/17.
 *
 * @author Reda.Housni-Alaoui
 */
public class SQLErrorCodesOrStateResolverTest {

	@Test
	public void testIsDuplicateKey_isDuplicateKey_usingSqlState() throws Exception {
		Properties props = new Properties();
		props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

		String databaseProductName = "MyCustom Database Engine";

		SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesOrStateResolver(props, databaseProductName);

		SQLException sqlException = new SQLException("test", "-104");

		boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
				sqlException));

		assertTrue(isDuplicateKey);
	}

	@Test
	public void testIsDuplicateKey_isDuplicateKey_usingNonIntSqlState() throws Exception {
		Properties props = new Properties();
		props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

		String databaseProductName = "MyCustom Database Engine";

		SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesOrStateResolver(props, databaseProductName);

		SQLException sqlException = new SQLException("test", "thisIsNotAnInt");

		boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
				sqlException));

		assertFalse(isDuplicateKey);
	}

	@Test
	public void testIsDuplicateKey_isDuplicateKey_usingNonNullSqlState() throws Exception {
		Properties props = new Properties();
		props.setProperty("MyCustom_Database_Engine.duplicateKeyCodes", "-104");

		String databaseProductName = "MyCustom Database Engine";

		SQLErrorCodesResolver sqlErrorCodesResolver = new SQLErrorCodesOrStateResolver(props, databaseProductName);

		SQLException sqlException = new SQLException("test", (String) null);

		boolean isDuplicateKey = sqlErrorCodesResolver.isDuplicateKeyViolation(new PersistenceException("error",
				sqlException));

		assertFalse(isDuplicateKey);
	}

}