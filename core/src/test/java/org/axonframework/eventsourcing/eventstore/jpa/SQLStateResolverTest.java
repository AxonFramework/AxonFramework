package org.axonframework.eventsourcing.eventstore.jpa;

import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Jochen Munz
 */
public class SQLStateResolverTest {

    @Test
    public void testDefaultResolver_duplicateKeyException() {
        SQLStateResolver resolver = new SQLStateResolver();

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(duplicateKeyException());

        assertTrue(isDuplicateKey);
    }

    @Test
    public void testDefaultResolver_integrityConstraintViolated() {
        SQLStateResolver resolver = new SQLStateResolver();

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(integrityContraintViolation());

        assertTrue(isDuplicateKey);
    }

    @Test
    public void testExplicitResolver_duplicateKeyException() {
        SQLStateResolver resolver = new SQLStateResolver("23505");

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(duplicateKeyException());

        assertTrue(isDuplicateKey);
    }


    @Test
    public void testExplicitResolver_integrityConstraintViolated() {
        SQLStateResolver resolver = new SQLStateResolver("23505");

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(integrityContraintViolation());

        assertFalse("A general state code should not be matched by the explicitly configured resolver", isDuplicateKey);
    }

    private Exception integrityContraintViolation() {
        return new SQLException("general state code", "23000");
    }


    private Exception duplicateKeyException() {
        return new SQLException("detailed state code", "23505");
    }

}