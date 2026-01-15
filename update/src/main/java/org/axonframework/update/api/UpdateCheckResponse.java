/*
 * Copyright (c) 2010-2026. Axon Framework
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
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the response from the UpdateChecker API, containing information about version upgrades and
 * vulnerabilities found in the artifacts used by the application.
 *
 * @param checkInterval   The interval in seconds at which the usage data should be checked.
 * @param upgrades        A list of found version upgrades, each containing details about the artifact and its latest
 *                        version.
 * @param vulnerabilities A list of found vulnerabilities, each containing details about the artifact, its severity, fix
 *                        version, and a URL for more information.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public record UpdateCheckResponse(
        int checkInterval,
        @Nonnull List<ArtifactAvailableUpgrade> upgrades,
        @Nonnull List<DetectedVulnerability> vulnerabilities
) {

    /**
     * Parses the response body from the anonymous usage reporter into a {@code UsageResponse} object. The body is
     * expected to be in a specific format where each line contains a key-value pair. The expected keys are:
     * <ul>
     *     <li>cd - Check interval in seconds</li>
     *     <li>vul - Vulnerability information in the format:
     *            groupId:artifactId:fixVersion:severity:moreInformationUrl</li>
     *     <li>upd - Update information in the format: groupId:artifactId:latestVersion</li>
     * </ul>
     * <p>
     * An example of the expected format:
     * <pre>
     * cd=86400
     * vul=org.axonframework:axon-conversion:1.0.0:HIGH:"https://example.com/vulnerability"
     * upd=org.axonframework:axon-messaging:5.0.1
     * </pre>
     *
     * @param body The response body as a string.
     * @return A {@code UsageResponse} object containing the parsed data.
     */
    @Nonnull
    public static UpdateCheckResponse fromRequest(@Nullable String body) {
        int checkInterval = 86400; // Default to 24 hours, in case request didn't work
        List<ArtifactAvailableUpgrade> upgrades = new ArrayList<>();
        List<DetectedVulnerability> vulnerabilities = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return new UpdateCheckResponse(checkInterval, List.of(), List.of());
        }
        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length < 2) {
                continue; // Skip lines without a value
            }
            String key = parts[0].trim();
            String value = parts[1].trim();

            switch (key) {
                case "cd":
                    checkInterval = parseCheckInterval(value);
                    break;
                case "vul":
                    parseVulnerability(value, vulnerabilities);
                    break;
                case "upd":
                    parseUpdate(value, vulnerabilities, upgrades);
                    break;
            }
        }
        return new UpdateCheckResponse(checkInterval, upgrades, vulnerabilities);
    }

    private static void parseUpdate(String val, List<DetectedVulnerability> vulnerabilities,
                                    List<ArtifactAvailableUpgrade> upgrades) {
        // Format: upd=groupId:artifactId:latestVersion
        String[] parts = parseIntoParts(val);
        if (parts.length == 3) {
            String groupId = parts[0];
            String artifactId = parts[1];
            String latestVersion = parts[2];
            upgrades.add(new ArtifactAvailableUpgrade(
                    groupId, artifactId, latestVersion
            ));
        }
    }

    private static void parseVulnerability(String val, List<DetectedVulnerability> vulnerabilities) {
        // Format: vul=groupId:artifactId:fixVersion:severity:moreInformationUrl
        String[] parts = parseIntoParts(val);
        if (parts.length == 5) {
            String groupId = parts[0];
            String artifactId = parts[1];
            String fixVersion = parts[2];
            DetectedVulnerabilitySeverity severity = parseVulnerability(parts[3]);
            String moreInformationUrl = parts[4];
            vulnerabilities.add(new DetectedVulnerability(
                    groupId, artifactId, severity, fixVersion, moreInformationUrl
            ));
        }
    }

    /**
     * Parses a string into parts, splitting on ':' while respecting quoted sections. Quoted sections can contain colons
     * without splitting, similar to how CSV parsing works.
     *
     * @param val the string to parse
     * @return an array of parts
     */
    private static String[] parseIntoParts(String val) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < val.length(); i++) {
            char c = val.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    private static DetectedVulnerabilitySeverity parseVulnerability(String value) {
        DetectedVulnerabilitySeverity severity;
        try {
            severity = DetectedVulnerabilitySeverity.valueOf(value);
        } catch (Exception e) {
            severity = DetectedVulnerabilitySeverity.UNKNOWN;
        }
        return severity;
    }

    private static int parseCheckInterval(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 86400; // Default to 24 hours, in case request didn't work;
        }
    }
}
