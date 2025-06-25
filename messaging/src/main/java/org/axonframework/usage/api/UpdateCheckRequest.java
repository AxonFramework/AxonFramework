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

package org.axonframework.usage.api;

import org.axonframework.common.annotation.Internal;

import java.util.List;

/**
 * Represents a request for usage data, including machine and instance identifiers, operating system details, JVM
 * information, Kotlin version, and a list of library versions.
 *
 * @param machineId  The unique identifier for the machine. This is a UUID that is generated and stored in the user's
 *                   home directory.
 * @param instanceId The unique identifier for the instance of the application. This is a UUID that is generated for
 *                   each JVM instance.
 * @param osName     The name of the operating system (e.g., "Linux", "Windows").
 * @param osVersion  The version of the operating system (e.g., "6.11.0-26-generic").
 * @param osArch     The architecture of the operating system (e.g., "amd64", "x86_64").
 * @param jvmVersion The version of the Java Virtual Machine (JVM) (e.g., "17.0.2").
 * @param jvmVendor  The vendor of the JVM (e.g., "AdoptOpenJDK", "Oracle").
 * @param kotlinVersion The version of Kotlin used in the application, or "none" if Kotlin is not used.
 * @param libraries  A list of library versions used in the application, each represented by a {@link Artifact}
 *                   object containing the group ID, artifact ID, and version of the library.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public record UpdateCheckRequest(
        String machineId,
        String instanceId,
        String osName,
        String osVersion,
        String osArch,
        String jvmVersion,
        String jvmVendor,
        String kotlinVersion,
        List<Artifact> libraries
) {

    /**
     * Serializes the usage request into a string format. Each field is represented as a key-value pair, with each pair
     * on a new line. Library versions are serialized in the format "groupId:artifactId:version".
     *
     * @return The String representation of the usage request.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("mid=").append(machineId).append("\n");
        sb.append("iid=").append(instanceId).append("\n");
        sb.append("osn=").append(osName).append("\n");
        sb.append("osv=").append(osVersion).append("\n");
        sb.append("osa=").append(osArch).append("\n");
        sb.append("jvr=").append(jvmVersion).append("\n");
        sb.append("jvn=").append(jvmVendor).append("\n");
        sb.append("ktv=").append(kotlinVersion).append("\n");

        for (Artifact library : libraries) {
            sb.append("lib=").append(library.groupId())
              .append(':').append(library.artifactId())
              .append(':').append(library.version())
              .append("\n");
        }

        return sb.toString();
    }
}
