/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.control.NodeInfo;
import org.axonframework.axonserver.connector.event.util.EventCipher;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: marc
 */
@ConfigurationProperties(prefix = "axon.axonserver")
public class AxonServerConfiguration {
    private static final int DEFAULT_GRPC_PORT = 8124;
    private static final String DEFAULT_SERVERS = "localhost";

    /**
     * Comma separated list of AxonDB servers. Each element is hostname or hostname:grpcPort. When no grpcPort is
     * specified, default port 8123 is used.
     */
    private String servers = DEFAULT_SERVERS;

    /**
     * clientId as it registers itself to AxonServer, must be unique
     */
    private String clientId = ManagementFactory.getRuntimeMXBean().getName();
    /**
     * application name, defaults to spring.application.name
     * multiple instances of the same application share the same application name, but each must have
     * a different clientId
     */
    private String componentName;

    /**
     * Token for access control
     */
    private String token;

    /**
     * Bounded context that this application operates in
     */
    private String context;
    /**
     * Certificate file for SSL
     */
    private String certFile;
    /**
     * Use TLS for connection to AxonServer
     */
    private boolean sslEnabled;

    /**
     * Initial number of permits send for message streams (events, commands, queries)
     */
    private Integer initialNrOfPermits = 1000;
    /**
     * Additional number of permits send for message streams (events, commands, queries) when application
     * is ready for more messages
     */
    private Integer nrOfNewPermits = 500;
    /**
     * Threshold at which application sends new permits to server
     */
    private Integer newPermitsThreshold = 500;

    /**
     * Number of threads executing commands
     */
    private int commandThreads = 10;
    /**
     * Number of threads executing queries
     */
    private int queryThreads = 10;

    /**
     * Interval (in ms.) application sends status updates on event processors to AxonServer
     */
    private int processorsNotificationRate = 500;

    /**
     * Initial delay (in ms.) before application sends first status update on event processors to AxonServer
     */
    private int processorsNotificationInitialDelay = 5000;

    private EventCipher eventCipher = new EventCipher();

    /**
     * Timeout (in ms) for keep alive requests
     */
    private long keepAliveTimeout = 5000;

    /**
     * Interval (in ms) for keep alive requests, 0 is keep-alive disabled
     */
    private long keepAliveTime = 0;
    private int snapshotPrefetch = 1;

    /**
     * Indicates whether the download advice message should be suppressed, even when default connection properties
     * (which are generally only used in DEV mode) are used. Defaults to false.
     */
    private boolean suppressDownloadMessage = false;

    /**
     *  GRPC max inbound message size, 0 keeps default value
     */
    private int maxMessageSize = 0;

    public AxonServerConfiguration() {
    }


    public String getServers() {
        return servers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getComponentName() {
        return componentName == null ? System.getProperty("axon.application.name", "Unnamed-" + clientId) : componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public void setServers(String routingServers) {
        this.servers = routingServers;
        suppressDownloadMessage = true;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCertFile() {
        return certFile;
    }

    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Integer getInitialNrOfPermits() {
        return initialNrOfPermits;
    }

    public void setInitialNrOfPermits(Integer initialNrOfPermits) {
        this.initialNrOfPermits = initialNrOfPermits;
    }

    public Integer getNrOfNewPermits() {
        return nrOfNewPermits;
    }

    public void setNrOfNewPermits(Integer nrOfNewPermits) {
        this.nrOfNewPermits = nrOfNewPermits;
    }

    public Integer getNewPermitsThreshold() {
        return newPermitsThreshold;
    }

    public void setNewPermitsThreshold(Integer newPermitsThreshold) {
        this.newPermitsThreshold = newPermitsThreshold;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public List<NodeInfo> routingServers() {
        String[] serverArr = servers.split(",");
        return Arrays.stream(serverArr).map(server -> {
            String[] s = server.trim().split(":");
            if( s.length > 1) {
                return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(Integer.valueOf(s[1])).build();
            }
            return NodeInfo.newBuilder().setHostName(s[0]).setGrpcPort(DEFAULT_GRPC_PORT).build();
        }).collect(Collectors.toList());
    }

    public EventCipher getEventCipher() {
        return eventCipher;
    }

    private void setEventSecretKey(String key) {
        if(key != null && key.length() > 0) {
            eventCipher = new EventCipher(key.getBytes(StandardCharsets.US_ASCII));
        }
    }

    public Integer getCommandThreads() {
        return commandThreads;
    }

    public void setCommandThreads(Integer commandThreads) {
        this.commandThreads = commandThreads;
    }

    public int getQueryThreads() {
        return queryThreads;
    }

    public void setQueryThreads(int queryThreads) {
        this.queryThreads = queryThreads;
    }

    public int getProcessorsNotificationRate() {
        return processorsNotificationRate;
    }

    public void setProcessorsNotificationRate(int processorsNotificationRate) {
        this.processorsNotificationRate = processorsNotificationRate;
    }

    public int getProcessorsNotificationInitialDelay() {
        return processorsNotificationInitialDelay;
    }

    public void setProcessorsNotificationInitialDelay(int processorsNotificationInitialDelay) {
        this.processorsNotificationInitialDelay = processorsNotificationInitialDelay;
    }

    public long getKeepAliveTimeout() {
        return this.keepAliveTimeout;
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    public void setCommandThreads(int commandThreads) {
        this.commandThreads = commandThreads;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public void setSuppressDownloadMessage(boolean suppressDownloadMessage) {
        this.suppressDownloadMessage = suppressDownloadMessage;
    }

    public boolean getSuppressDownloadMessage() {
        return suppressDownloadMessage;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public int getSnapshotPrefetch() {
        return snapshotPrefetch;
    }

    public void setSnapshotPrefetch(int snapshotPrefetch) {
        this.snapshotPrefetch = snapshotPrefetch;
    }

    @SuppressWarnings("unused")
    public static class Builder {
        private AxonServerConfiguration instance;

        public Builder() {
            instance = new AxonServerConfiguration();
            instance.initialNrOfPermits = 1000;
            instance.nrOfNewPermits = 500;
            instance.newPermitsThreshold = 500;
        }

        public Builder ssl(String certFile) {
            instance.certFile = certFile;
            instance.sslEnabled = true;
            return this;
        }

        public Builder token(String token) {
            instance.token = token;
            return this;
        }

        public Builder context(String context) {
            instance.context = context;
            return this;
        }

        public Builder flowControl(int initialNrOfPermits, int nrOfNewPermits, int newPermitsThreshold) {
            instance.initialNrOfPermits = initialNrOfPermits;
            instance.nrOfNewPermits = nrOfNewPermits;
            instance.newPermitsThreshold = newPermitsThreshold;
            return this;
        }

        public Builder setEventSecretKey(String key) {
            instance.setEventSecretKey(key);
            return this;
        }

        public Builder eventCipher(EventCipher eventCipher) {
            instance.eventCipher = eventCipher;
            return this;
        }

        public Builder maxMessageSize(int maxMessageSize) {
            instance.maxMessageSize = maxMessageSize;
            return this;
        }

        public Builder snapshotPrefetch(int snapshotPrefetch) {
            instance.snapshotPrefetch = snapshotPrefetch;
            return this;
        }

        public AxonServerConfiguration build() {
            return instance;
        }

        public Builder servers(String servers) {
            instance.setServers(servers);
            return this;
        }

        public Builder suppressDownloadMessage() {
            instance.setSuppressDownloadMessage(true);
            return this;
        }

        public Builder componentName(String componentName) {
            instance.setComponentName(componentName);
            return this;
        }

        public Builder clientId(String clientId) {
            instance.setClientId(clientId);
            return this;
        }
    }

    public static Builder builder() {
        Builder builder = new Builder();
        if (Boolean.getBoolean("axon.axonserver.suppressDownloadMessage")) {
            builder.suppressDownloadMessage();
        }

        return builder;
    }
}
