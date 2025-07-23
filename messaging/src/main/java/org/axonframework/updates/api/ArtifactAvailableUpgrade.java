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

package org.axonframework.updates.api;

import java.util.Objects;

/**
 * Represents an upgrade suggestion for a specific artifact version by the AxonIQ Update Checker API.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class ArtifactAvailableUpgrade {

    private final String groupId;
    private final String artifactId;
    private final String latestVersion;

    /**
     * Constructs an {@code ArtifactAvailableUpgrade} with the given {@code groupId}, {@code artifactId}, and
     * {@code latestVersion}.
     *
     * @param groupId       The group ID of the library.
     * @param artifactId    The artifact ID of the library.
     * @param latestVersion The latest version of the library available for upgrade.
     */
    public ArtifactAvailableUpgrade(String groupId,
                                    String artifactId,
                                    String latestVersion) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.latestVersion = latestVersion;
    }

    /**
     * Returns the group identifier of this available artifact upgrade.
     *
     * @return The group identifier of this available artifact upgrade.
     */
    public String groupId() {
        return groupId;
    }

    /**
     * Returns the artifact identifier of this available artifact upgrade.
     *
     * @return The artifact identifier of this available artifact upgrade.
     */
    public String artifactId() {
        return artifactId;
    }

    /**
     * Returns the latest version of this available artifact upgrade.
     *
     * @return The latest version of this available artifact upgrade.
     */
    public String latestVersion() {
        return latestVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArtifactAvailableUpgrade that = (ArtifactAvailableUpgrade) o;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(latestVersion, that.latestVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, latestVersion);
    }

    @Override
    public String toString() {
        return "ArtifactAvailableUpgrade{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", latestVersion='" + latestVersion + '\'' +
                '}';
    }
}