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
 * Represents an artifact with its group ID, artifact ID, and version.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class Artifact {

    private final String groupId;
    private final String artifactId;
    private final String version;

    /**
     * Constructs a {@code DetectedVulnerability} with the given {@code groupId}, {@code artifactId}, and
     * {@code version}.
     *
     * @param groupId    The group ID of the artifact.
     * @param artifactId The artifact ID of the artifact.
     * @param version    The version of the artifact.
     */
    public Artifact(String groupId,
                    String artifactId,
                    String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * Returns the group identifier of this artifact.
     *
     * @return The group identifier of this artifact.
     */
    public String groupId() {
        return groupId;
    }

    /**
     * Returns a short version of the group ID, to save bytes over the wire.
     *
     * @return The short version of the group ID, or the original if it can't be shortened.
     */
    public String shortGroupId() {
        if (groupId.startsWith("org.axonframework.extensions")) {
            if (groupId.length() == 28) {
                return "ext";
            }
            return "ext." + groupId.substring(29);
        }
        if (groupId.startsWith("org.axonframework")) {
            if (groupId.length() == 17) {
                return "fw";
            }
            return "fw." + groupId.substring(18);
        }
        if (groupId.startsWith("io.axoniq")) {
            if (groupId.length() == 9) {
                return "iq";
            }
            return "iq." + groupId.substring(10);
        }
        return groupId;
    }

    /**
     * Returns the artifact identifier of this artifact.
     *
     * @return The artifact identifier of this artifact.
     */
    public String artifactId() {
        return artifactId;
    }

    /**
     * Returns the version of this artifact.
     *
     * @return The version of this artifact.
     */
    public String version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Artifact artifact = (Artifact) o;
        return Objects.equals(groupId, artifact.groupId)
                && Objects.equals(artifactId, artifact.artifactId)
                && Objects.equals(version, artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return "Artifact{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
