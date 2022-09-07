/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.common.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link JdbcUtils} class static methods
 * 
 * @author Albert Attard (Java Creed)
 * @see JdbcUtils
 */
class JdbcUtilsTest {

    /**
     * Tries to read from an empty result set. The method is expected to return
     * {@code null}
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_EmptyResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(false);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads {@code null} from the result set. The result set here returns
     * {@code null}.
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(null);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads a value from the results set. The method should return the value that
     * was read.
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NonNullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(10L);
        assertEquals(Long.valueOf(10L), JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads a null value but the result set returns 0L while the wasNull() method
     * returns false, which indicates that the value read from the result set was
     * actually {@code null}.
     * 
     * This test was added to address issue
     * <a href="https://github.com/AxonFramework/AxonFramework/issues/636">#638</a>
     * 
     * @throws Exception
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NonNullValue_WasNull() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);
        assertNull(JdbcUtils.nextAndExtract(resultSet, 1, Long.class));
    }
}
