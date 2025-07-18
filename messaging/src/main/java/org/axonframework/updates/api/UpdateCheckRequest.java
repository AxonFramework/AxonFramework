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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Represents an UpdateChecker request, including machine and instance identifiers, operating system details, JVM
 * information, Kotlin version, and a list of library versions.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class UpdateCheckRequest {

    private final String machineId;
    private final String instanceId;
    private final String osName;
    private final String osVersion;
    private final String osArch;
    private final String jvmVersion;
    private final String jvmVendor;
    private final String kotlinVersion;
    private final List<Artifact> libraries;

    /**
     * Constructs an {@code UpdateCheckRequest} with the given {@code machineId}, {@code instanceId}, {@code osName},
     * {@code osVersion}, {@code osArch}, {@code jvmVersion}, {@code jvmVendor}, {@code kotlinVersion}, and
     * {@code libraries}.
     *
     * @param machineId     The unique identifier for the machine. This is a UUID that is generated and stored in the
     *                      user's home directory.
     * @param instanceId    The unique identifier for the instance of the application. This is a UUID that is generated
     *                      for each JVM instance.
     * @param osName        The name of the operating system (e.g., "Linux", "Windows").
     * @param osVersion     The version of the operating system (e.g., "6.11.0-26-generic").
     * @param osArch        The architecture of the operating system (e.g., "amd64", "x86_64").
     * @param jvmVersion    The version of the Java Virtual Machine (JVM) (e.g., "17.0.2").
     * @param jvmVendor     The vendor of the JVM (e.g., "AdoptOpenJDK", "Oracle").
     * @param kotlinVersion The version of Kotlin used in the application, or "none" if Kotlin is not used.
     * @param libraries     A list of library versions used in the application, each represented by a {@link Artifact}
     *                      object containing the group ID, artifact ID, and version of the library.
     */
    public UpdateCheckRequest(String machineId,
                              String instanceId,
                              String osName,
                              String osVersion,
                              String osArch,
                              String jvmVersion,
                              String jvmVendor,
                              String kotlinVersion,
                              List<Artifact> libraries) {
        this.machineId = machineId;
        this.instanceId = instanceId;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArch = osArch;
        this.jvmVersion = jvmVersion;
        this.jvmVendor = jvmVendor;
        this.kotlinVersion = kotlinVersion;
        this.libraries = libraries;
    }

    /**
     * Returns the machine identifier of this update check request.
     *
     * @return The machine identifier of this update check request.
     */
    public String machineId() {
        return machineId;
    }

    /**
     * Returns the instance identifier of this update check request.
     *
     * @return The instance identifier of this update check request.
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * Returns the operating system name of this update check request.
     *
     * @return The operating system name of this update check request.
     */
    public String osName() {
        return osName;
    }

    /**
     * Returns the operating system version of this update check request.
     *
     * @return The operating system version of this update check request.
     */
    public String osVersion() {
        return osVersion;
    }

    /**
     * Returns the operating system architecture of this update check request.
     *
     * @return The operating system architecture of this update check request.
     */
    public String osArch() {
        return osArch;
    }

    /**
     * Returns the JVM version of this update check request.
     *
     * @return The JVM version of this update check request.
     */
    public String jvmVersion() {
        return jvmVersion;
    }

    /**
     * Returns the JVM vendor of this update check request.
     *
     * @return The JVM vendor of this update check request.
     */
    public String jvmVendor() {
        return jvmVendor;
    }

    /**
     * Returns the kotlin version of this update check request.
     *
     * @return The kotlin version of this update check request.
     */
    public String kotlinVersion() {
        return kotlinVersion;
    }

    /**
     * Returns the collection of {@link Artifact libraries} of this update check request.
     *
     * @return The collection of {@link Artifact libraries} of this update check request.
     */
    public List<Artifact> libraries() {
        return libraries;
    }

    /**
     * Converts the usage request into a query string format suitable for HTTP requests. All values are properly URL
     * encoded.
     *
     * @return The query string representation of the usage request.
     */
    public String toQueryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("os=").append(encode(osName + "; " + osVersion + "; " + osArch))
          .append("&java=").append(encode(jvmVersion + "; " + jvmVendor))
          .append("&kotlin=").append(encode(kotlinVersion));
        for (Artifact library : libraries) {
            sb.append("&lib-").append(library.shortGroupId()).append(".").append(library.artifactId()).append("=")
              .append(encode(library.version()));
        }
        return sb.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to encode [" + value + "] to an URL.", e);
        }
    }

    /**
     * Converts the usage request into a user agent string format.
     *
     * @return The user agent string representation of the usage request.
     */
    public String toUserAgent() {
        String axonBaseVersion = getAxonBaseVersion();
        return String.format(
                "AxonIQ UpdateChecker/%s (Java %s %s; %s; %s; %s)",
                axonBaseVersion,
                jvmVersion, jvmVendor, osName, osVersion, osArch
        );
    }

    private String getAxonBaseVersion() {
        return libraries.stream()
                        .filter(a -> a.groupId().equals("org.axonframework"))
                        .filter(a -> a.artifactId().equals("axon-messaging"))
                        .map(Artifact::version)
                        .findFirst().orElse("5.0.0");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UpdateCheckRequest that = (UpdateCheckRequest) o;
        return Objects.equals(machineId, that.machineId)
                && Objects.equals(instanceId, that.instanceId)
                && Objects.equals(osName, that.osName)
                && Objects.equals(osVersion, that.osVersion)
                && Objects.equals(osArch, that.osArch)
                && Objects.equals(jvmVersion, that.jvmVersion)
                && Objects.equals(jvmVendor, that.jvmVendor)
                && Objects.equals(kotlinVersion, that.kotlinVersion)
                && Objects.equals(libraries, that.libraries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineId,
                            instanceId,
                            osName,
                            osVersion,
                            osArch,
                            jvmVersion,
                            jvmVendor,
                            kotlinVersion,
                            libraries);
    }

    @Override
    public String toString() {
        return "UpdateCheckRequest{" +
                "machineId='" + machineId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", osArch='" + osArch + '\'' +
                ", jvmVersion='" + jvmVersion + '\'' +
                ", jvmVendor='" + jvmVendor + '\'' +
                ", kotlinVersion='" + kotlinVersion + '\'' +
                ", libraries=" + libraries +
                '}';
    }
}