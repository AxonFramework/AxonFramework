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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Assert;

/**
 * CommandTargetResolver implementation that uses MetaData entries to extract the identifier and optionally the version
 * of the aggregate that the command targets.
 * <p/>
 * While this may require duplication of data (as the identifier is already included in the payload as well), it is a
 * more performing alternative to a reflection based CommandTargetResolvers.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataCommandTargetResolver implements CommandTargetResolver {

    private final String identifierKey;
    private final String versionKey;

    /**
     * Initializes the MetaDataCommandTargetResolver to use the given {@code identifierKey} as the MetaData
     * key to the aggregate identifier, and a {@code null} (ignored) version.
     * <p/>
     * When the given {@code identifierKey} is not present in a command's MetaData, {@link
     * #resolveTarget(CommandMessage)} will raise an {@link IllegalArgumentException}
     *
     * @param identifierKey The key of the meta data field containing the aggregate identifier
     */
    public MetaDataCommandTargetResolver(String identifierKey) {
        this(identifierKey, null);
    }

    /**
     * Initializes the MetaDataCommandTargetResolver to use the given {@code identifierKey} as the MetaData
     * key to the aggregate identifier, and the given {@code versionKey} as key to the (optional) version entry.
     * <p/>
     * When the given {@code identifierKey} is not present in a command's MetaData, {@link
     * #resolveTarget(CommandMessage)} will raise an {@link IllegalArgumentException}
     *
     * @param identifierKey The key of the meta data field containing the aggregate identifier
     * @param versionKey    The key of the meta data field containing the expected aggregate version. A
     *                      {@code null} value may be provided to ignore the version
     */
    public MetaDataCommandTargetResolver(String identifierKey, String versionKey) {
        this.versionKey = versionKey;
        this.identifierKey = identifierKey;
    }

    @Override
    public VersionedAggregateIdentifier resolveTarget(CommandMessage<?> command) {
        String identifier = (String) command.getMetaData().get(identifierKey);
        Assert.notNull(identifier, () -> "The MetaData for the command does not exist or contains a null value");
        Long version = (Long) (versionKey == null ? null : command.getMetaData().get(versionKey));
        return new VersionedAggregateIdentifier(identifier, version);
    }
}
