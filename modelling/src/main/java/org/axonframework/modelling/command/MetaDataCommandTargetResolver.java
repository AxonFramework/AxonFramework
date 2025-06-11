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

package org.axonframework.modelling.command;

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;

/**
 * CommandTargetResolver implementation that uses MetaData entries to extract the identifier.
 * <p/>
 * While this may require duplication of data (as the identifier is already included in the payload as well), it is a
 * more performing alternative to a reflection based CommandTargetResolvers.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataCommandTargetResolver implements CommandTargetResolver {

    private final String identifierKey;

    /**
     * Initializes the MetaDataCommandTargetResolver to use the given {@code identifierKey} as the MetaData key to the
     * aggregate identifier.
     * <p/>
     * When the given {@code identifierKey} is not present in a command's MetaData,
     * {@link #resolveTarget(CommandMessage)} will raise an {@link IllegalArgumentException}
     *
     * @param identifierKey The key of the metadata field containing the aggregate identifier
     */
    public MetaDataCommandTargetResolver(String identifierKey) {
        this.identifierKey = identifierKey;
    }

    @Override
    public String resolveTarget(@Nonnull CommandMessage<?> command) {
        String identifier = command.getMetaData().get(identifierKey).toString();
        if (identifier == null) {
            throw new IdentifierMissingException(
                    "The MetaData for the command does not contain an identifier under key [" + identifierKey + "]"
            );
        }
        return identifier;
    }
}
