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

import java.util.List;

/**
 * Represents a request for usage data, including machine and instance identifiers, operating system details, JVM
 * information, Kotlin version, and a list of library versions.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public record UsageRequest(
        String machineId,
        String instanceId,
        String osName,
        String osVersion,
        String osArch,
        String jvmVersion,
        String jvmVendor,
        String kotlinVersion,
        List<LibraryVersion> libraries
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

        for (LibraryVersion library : libraries) {
            sb.append("lib=").append(library.groupId())
              .append(':').append(library.artifactId())
              .append(':').append(library.version())
              .append("\n");
        }

        return sb.toString();
    }
}
