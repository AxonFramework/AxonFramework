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

package org.axonframework.updates;

import org.axonframework.common.BuilderUtils;
import org.axonframework.updates.api.UpdateCheckRequest;
import org.axonframework.updates.api.UpdateCheckResponse;
import org.axonframework.updates.configuration.UsagePropertyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Client for checking for updates and sending anonymous usage data to the AxonIQ servers. This client uses the
 * {@link UsagePropertyProvider} to determine the URL to send the data to.
 *
 * @author Mitchell Herrijgers
 * @since 4.12.0
 */
public class UpdateCheckerHttpClient {

    private final static Logger logger = LoggerFactory.getLogger(UpdateCheckerHttpClient.class);

    private final UsagePropertyProvider userProperties;

    /**
     * Creates a new {@code UpdateCheckerHttpClient} with the given {@link UsagePropertyProvider}. The client will use
     * the properties to determine the URL to send the usage data to.
     *
     * @param userProperties The {@link UsagePropertyProvider} to use for retrieving the URL and other properties.
     */
    public UpdateCheckerHttpClient(UsagePropertyProvider userProperties) {
        BuilderUtils.assertNonNull(userProperties, "The userProperties must not be null.");
        this.userProperties = userProperties;
    }

    /**
     * Sends a usage request to the AxonIQ servers. If {@code firstRequest} is true, it will send a POST request,
     * otherwise it will send a PUT request.
     *
     * @param updateCheckRequest The {@link UpdateCheckRequest} to send.
     * @param firstRequest       Whether this is the first request or not.
     * @return An {@link Optional} containing the {@link UpdateCheckResponse} if the request was successful, or empty if
     * it failed.
     */
    public Optional<UpdateCheckResponse> sendRequest(UpdateCheckRequest updateCheckRequest,
                                                     boolean firstRequest) {
        String url = userProperties.getUrl() + "?" + updateCheckRequest.toQueryString();

        try {
            logger.debug("Reporting anonymous usage data to AxonIQ servers at: {}", url);

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000); // 10 seconds
            connection.setInstanceFollowRedirects(true);
            // Set headers
            connection.setRequestProperty("User-Agent", updateCheckRequest.toUserAgent());
            connection.setRequestProperty("X-Machine-Id", updateCheckRequest.machineId());
            connection.setRequestProperty("X-Instance-Id", updateCheckRequest.instanceId());
            connection.setRequestProperty("X-Uptime", String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
            connection.setRequestProperty("X-First-Run", firstRequest ? "true" : "false");

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                logger.info("Failed to report anonymous usage data, received status code: {}", statusCode);
                return Optional.empty();
            }

            // Read response
            String responseBody = readResponse(connection);
            logger.debug("Reported anonymous usage data successfully, received response: {}", responseBody);
            return Optional.of(UpdateCheckResponse.fromRequest(responseBody));
        } catch (Exception e) {
            logger.warn("Failed to report anonymous usage data", e);
            return Optional.empty();
        }
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
