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

package org.axonframework.usage;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.usage.api.LibraryVersion;
import org.axonframework.usage.api.UsageRequest;
import org.axonframework.usage.api.UsageResponse;
import org.axonframework.usage.common.DelayedTask;
import org.axonframework.usage.configuration.UsagePropertyProvider;
import org.axonframework.usage.detection.AxonVersionDetector;
import org.axonframework.usage.detection.KotlinVersion;
import org.axonframework.usage.detection.MachineId;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.axonframework.usage.UpdateChecker.logger;

/**
 * This task reports anonymous usage data to AxonIQ's telemetry endpoint. In return, it receives information about
 * available upgrades and vulnerabilities in the Axon libraries used. These vulnerabilities and upgrades are logged.
 * <p>
 * The task is scheduled to run periodically, with an initial delay of 1 second. The interval between runs is determined
 * by the response from the telemetry endpoint.
 * <p>
 * The task will retry reporting in case of an error, with an exponential backoff strategy. The backoff factor is
 * increased with each failed attempt, up to a maximum of 60 seconds. The first request is sent as a POST request,
 * subsequent requests are sent as PUT requests.
 * <p>
 * The task will not run if the user has opted out of anonymous usage reporting. See {@link UpdateChecker} for
 * more details on how the opt-out is determined.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class UpdateCheckTask implements Runnable {
    private final HttpClient client;
    private final UsagePropertyProvider userProperties;
    private int errorRetryBackoffFactor = 1;
    private boolean firstRequest = true;

    /**
     * Starts the anonymous usage reporting task. If the user has opted out of anonymous usage reporting, the task will
     * not be started.
     */
    public static void start() {
        try {
            UsagePropertyProvider userProperties = UsagePropertyProvider.create();
            if (userProperties.getDisabled()) {
                logger.info("Anonymous usage reporting is opted out by the user. Skipping task initialization.");
                return;
            }
            DelayedTask.of(new UpdateCheckTask(userProperties), 1000);
        } catch (Exception e) {
            logger.warn("Failed to start Anonymous Usage Collector task.", e);
        }
    }

    private UpdateCheckTask(UsagePropertyProvider userProperties) {
        this.userProperties = userProperties;
        this.client = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build();
    }

    @Override
    public void run() {
        try {
            UsageRequest requestBody = buildRequest();
            String serializedRequestBody = requestBody.serialize();
            logger.debug("Reporting anonymous usage data:\n{}", serializedRequestBody);

            HttpRequest.Builder baseRequestBuilder = HttpRequest.newBuilder()
                                                                .uri(URI.create(userProperties.getUrl()))
                                                                .timeout(Duration.ofSeconds(5))
                                                                .headers("Content-Type", "text/plain;charset=UTF-8");

            HttpRequest request;
            if (firstRequest) {
                request = baseRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(serializedRequestBody))
                        .build();
            } else {
                request = baseRequestBuilder
                        .PUT(HttpRequest.BodyPublishers.ofString(serializedRequestBody))
                        .build();
            }

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.debug("Failed to report anonymous usage data, received status code: {}", response.statusCode());
                scheduleErrorRetry();
                return;
            }
            logger.debug("Successfully reported anonymous usage data, response: {}", response.body());
            UsageResponse usageResponse = UsageResponse.fromRequest(response.body());
            logUpgradesIfAny(requestBody, usageResponse);

            logger.debug("Next check interval: {} seconds", usageResponse.checkInterval());
            DelayedTask.of(this, usageResponse.checkInterval() * 1000);
            errorRetryBackoffFactor = 1; // Reset backoff factor on a successful report
            firstRequest = false;
        } catch (Exception e) {
            logger.warn("Failed to report anonymous usage data", e);

            scheduleErrorRetry();
        }
    }

    private void logUpgradesIfAny(UsageRequest requestBody, UsageResponse usageResponse) {
        boolean hasUpgrades = !usageResponse.upgrades().isEmpty();
        boolean hasVulnerabilities = !usageResponse.vulnerabilities().isEmpty();
        if (hasVulnerabilities) {
            logger.error("AxonIQ has found the following vulnerabilities in your Axon libraries:");
            logVulnerabilities(usageResponse);

            if (hasUpgrades) {
                logger.info("Additionally, AxonIQ has found an upgrade(s) for your Axon libraries:");
            }
        } else if(hasUpgrades) {
            logger.info("AxonIQ has found the following dependency upgrade(s):");
            logUpgrades(requestBody, usageResponse);
        } else {
            logger.info("All your AxonIQ libraries are up-to-date, no upgrades or vulnerabilities found.");
        }
    }

    private static void logUpgrades(UsageRequest requestBody, UsageResponse usageResponse) {
        int longestNameLength = usageResponse.upgrades().stream()
                                             .mapToInt(upgrade -> upgrade.groupId().length() + upgrade.artifactId().length() + 1)
                                             .max()
                                             .orElse(0);

        int longestVersionLength =requestBody.libraries().stream()
                                                .mapToInt(lib -> lib.version().length())
                                                .max()
                                                .orElse(0);
        usageResponse.upgrades().forEach(upgrade -> {
            String currentVersion = requestBody.libraries().stream()
                                               .filter(v -> v.artifactId().equals(upgrade.artifactId()) && v.groupId()
                                                                                                            .equals(upgrade.groupId()))
                                               .findFirst()
                                               .map(LibraryVersion::version)
                                               .orElse("unknown");
            logger.info("{}:{} {} {}{} -> {}",
                        upgrade.groupId(),
                        upgrade.artifactId(),
                        longestNameLength > 0 ? ".".repeat((longestNameLength - upgrade.groupId().length() - upgrade.artifactId().length() - 1) + 2) : "",
                        longestVersionLength > 0 ? " ".repeat(longestVersionLength - currentVersion.length()) : "",
                        currentVersion,
                        upgrade.latestVersion()
            );
        });
    }

    private static void logVulnerabilities(UsageResponse usageResponse) {
        usageResponse.vulnerabilities().forEach(vulnerability -> {
            logger.error(" - Severity: {}, Group: {}, Artifact: {}, Fix Version: {}, Description: {}",
                         vulnerability.severity(),
                         vulnerability.groupId(),
                         vulnerability.artifactId(),
                         vulnerability.fixVersion(),
                         vulnerability.description()
            );
        });
    }

    private void scheduleErrorRetry() {
        errorRetryBackoffFactor++;
        int nextInvocationTime = Math.min((int) ((Math.pow(2, errorRetryBackoffFactor)) * 1000), 60000);
        DelayedTask.of(this, nextInvocationTime);
    }

    @Nonnull
    private UsageRequest buildRequest() {
        String jvmVendor = System.getProperty("java.vendor");
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        String installationId = UUID.randomUUID().toString();

        return new UsageRequest(
                MachineId.get(),
                installationId,
                osName,
                osVersion,
                osArch,
                javaVersion,
                jvmVendor,
                KotlinVersion.get(),
                AxonVersionDetector.safeDetectAxonModules()
        );
    }
}
