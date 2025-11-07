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

package org.axonframework.eventsourcing.eventstore.jpa;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Jochen Munz
 */
class SQLStateResolverTest {

    @Test
    void defaultResolver_duplicateKeyException() {
        SQLStateResolver resolver = new SQLStateResolver();

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(duplicateKeyException());

        assertTrue(isDuplicateKey);
    }

    @Test
    void defaultResolver_integrityConstraintViolated() {
        SQLStateResolver resolver = new SQLStateResolver();

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(integrityContraintViolation());

        assertTrue(isDuplicateKey);
    }

    @Test
    void explicitResolver_duplicateKeyException() {
        SQLStateResolver resolver = new SQLStateResolver("23505");

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(duplicateKeyException());

        assertTrue(isDuplicateKey);
    }


    @Test
    void explicitResolver_integrityConstraintViolated() {
        SQLStateResolver resolver = new SQLStateResolver("23505");

        boolean isDuplicateKey = resolver.isDuplicateKeyViolation(integrityContraintViolation());

        assertFalse(isDuplicateKey, "A general state code should not be matched by the explicitly configured resolver");
    }

    private Exception integrityContraintViolation() {
        return new SQLException("general state code", "23000");
    }


    private Exception duplicateKeyException() {
        return new SQLException("detailed state code", "23505");
    }

}