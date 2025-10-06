/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.eventhandling.processors.streaming.token.store.jdbc;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventhandling.processors.streaming.token.store.TokenStore;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.temporal.TemporalAmount;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Configuration for the {@link JdbcTokenStore}.
 * <p>
 * Can be used to modify the {@link JdbcTokenStore}'s settings.
 *
 * @param schema       A {@link TokenSchema} which describes a JDBC token entry for this {@link TokenStore}.
 * @param claimTimeout A timeout specifying the time after which this process will force a claim.
 * @param nodeId       The id as a {@link String} to identify ownership of the tokens.
 * @param contentType  The content type as a {@link Class} to which a {@link TrackingToken} should be serialized.
 * @author Jens Mayer
 * @since 5.0.0
 */
public record JdbcTokenStoreConfiguration(
        @Nonnull TokenSchema schema,
        @Nonnull TemporalAmount claimTimeout,
        @Nonnull String nodeId,
        @Nullable Class<?> contentType
) {

    /**
     * A {@code JdbcTokenStoreConfiguration} instance with the following default values:
     * <ul>
     *     <li>The {@code schema} defaults to {@code new TokenSchema()}.</li>
     *     <li>The {@code claimTimeout} defaults to 10 seconds.</li>
     *     <li>The {@code nodeId} defaults to the name of the managed bean for the runtime system of the Java virtual machine.</li>
     *     <li>The {@code contentType} defaults to a {@code byte[]}-{@link Class}.</li>
     * </ul>
     */
    public static final JdbcTokenStoreConfiguration DEFAULT = new JdbcTokenStoreConfiguration(
            new TokenSchema(), Duration.ofSeconds(10), ManagementFactory.getRuntimeMXBean().getName(), byte[].class
    );

    /**
     * Compact constructor validating that the given {@code nodeId} is non-empty and non-null.
     */
    @SuppressWarnings("MissingJavadoc")
    public JdbcTokenStoreConfiguration {
        assertNonEmpty(nodeId, "The nodeId may not be empty.");
    }

    /**
     * Sets the {@code schema} which describes a JDBC token entry for this {@link TokenStore}.
     * <p>
     * Defaults to a default {@link TokenSchema} instance.
     *
     * @param schema a {@link TokenSchema} which describes a JDBC token entry for this {@link TokenStore}
     * @return The configuration itself, for fluent API usage.
     */
    public JdbcTokenStoreConfiguration schema(@Nonnull TokenSchema schema) {
        assertNonNull(schema, "The TokenSchema may not be null.");
        return new JdbcTokenStoreConfiguration(schema, claimTimeout, nodeId, contentType);
    }

    /**
     * Sets the {@code claimTimeout} specifying the amount of time a process will wait after which this process will
     * force a claim of a {@link TrackingToken}.
     * <p>
     * Thus, if a claim has not been updated for the given {@code claimTimeout}, this process will 'steal' the claim.
     * Defaults to a duration of 10 seconds.
     *
     * @param claimTimeout a timeout specifying the time after which this process will force a claim
     * @return The configuration itself, for fluent API usage.
     */
    public JdbcTokenStoreConfiguration claimTimeout(@Nonnull TemporalAmount claimTimeout) {
        assertNonNull(claimTimeout, "The claim timeout may not be null");
        return new JdbcTokenStoreConfiguration(schema, claimTimeout, nodeId, contentType);
    }

    /**
     * Sets the {@code nodeId} to identify ownership of the tokens.
     * <p>
     * Defaults to the name of the managed bean for the runtime system of the Java virtual machine.
     *
     * @param nodeId the id as a {@link String} to identify ownership of the tokens
     * @return The configuration itself, for fluent API usage.
     */
    public JdbcTokenStoreConfiguration nodeId(@Nonnull String nodeId) {
        assertNonEmpty(nodeId, "The nodeId may not be null or empty.");
        return new JdbcTokenStoreConfiguration(schema, claimTimeout, nodeId, contentType);
    }

    /**
     * Sets the {@code contentType} to which a {@link TrackingToken} should be serialized.
     * <p>
     * Defaults to a {@code byte[]} {@link Class} type.
     *
     * @param contentType the content type as a {@link Class} to which a {@link TrackingToken} should be serialized
     * @return The configuration itself, for fluent API usage.
     */
    public JdbcTokenStoreConfiguration contentType(@Nonnull Class<?> contentType) {
        assertNonNull(contentType, "The content type may not be null");
        return new JdbcTokenStoreConfiguration(schema, claimTimeout, nodeId, contentType);
    }
}