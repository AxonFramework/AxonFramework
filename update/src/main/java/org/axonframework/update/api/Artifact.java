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

package org.axonframework.update.api;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

/**
 * Represents an artifact with its group ID, artifact ID, and version.
 *
 * @param groupId    The group ID of the artifact.
 * @param artifactId The artifact ID of the artifact.
 * @param version    The version of the artifact.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public record Artifact(
        @Nonnull String groupId,
        @Nonnull String artifactId,
        @Nonnull String version
) {

    /**
     * Returns a short version of the group ID, to save bytes over the wire.
     *
     * @return The short version of the group ID, or the original if it can't be shortened.
     */
    public String shortGroupId() {
        if (groupId.startsWith("org.axonframework.extensions")) {
            if(groupId.length() == 28) {
                return "ext";
            }
            return "ext." + groupId.substring(29);
        }
        if (groupId.startsWith("org.axonframework")) {
            if(groupId.length() == 17) {
                return "fw";
            }
            return "fw." + groupId.substring(18);
        }
        if (groupId.startsWith("io.axoniq")) {
            if(groupId.length() == 9) {
                return "iq";
            }
            return "iq." + groupId.substring(10);
        }
        return groupId;
    }

}
