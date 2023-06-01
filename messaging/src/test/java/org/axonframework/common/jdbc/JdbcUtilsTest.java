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

import org.junit.jupiter.api.*;

import java.sql.ResultSet;

import static org.axonframework.common.jdbc.JdbcUtils.nextAndExtract;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the {@link JdbcUtils} class static methods
 *
 * @author Albert Attard (Java Creed)
 * @see JdbcUtils
 */
class JdbcUtilsTest {

    /**
     * Tries to read from an empty result set. The method is expected to return {@code null}
     *
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_EmptyResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(false);
        assertNull(nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads {@code null} from the result set. The result set here returns {@code null}.
     *
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(null);
        assertNull(nextAndExtract(resultSet, 1, Long.class));
    }

    @Test
    void nextAndExtractWithDefault_DefaultValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Long defaultValue = 42L;

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(null);
        assertEquals(defaultValue, nextAndExtract(resultSet, 1, Long.class, defaultValue));
    }

    /**
     * Reads a value from the results set. The method should return the value that was read.
     *
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NonNullValue() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(10L);
        assertEquals(Long.valueOf(10L), nextAndExtract(resultSet, 1, Long.class));
    }

    /**
     * Reads a null value but the result set returns 0L while the wasNull() method returns false, which indicates that
     * the value read from the result set was actually {@code null}.
     * <p>
     * This test was added to address issue
     * <a href="https://github.com/AxonFramework/AxonFramework/issues/636">#638</a>
     *
     * @see JdbcUtils#nextAndExtract(ResultSet, int, Class)
     * @see JdbcUtils#extract(ResultSet, int, Class)
     */
    @Test
    void nextAndExtract_NonNullValue_WasNull() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);
        assertNull(nextAndExtract(resultSet, 1, Long.class));
    }

    @Test
    void nextAndExtractWithDefault_NonNullValue_WasDefault() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Long defaultValue = 42L;

        when(resultSet.next()).thenReturn(true);
        when(resultSet.getObject(eq(1), eq(Long.class))).thenReturn(0L);
        when(resultSet.wasNull()).thenReturn(true);
        assertEquals(defaultValue, nextAndExtract(resultSet, 1, Long.class, defaultValue));
    }
}
