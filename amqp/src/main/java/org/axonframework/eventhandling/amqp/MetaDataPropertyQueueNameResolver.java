/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.amqp;

import org.axonframework.eventhandling.Cluster;

/**
 * QueueNameResolver implementation that looks for MetaData properties of a Cluster to decide on the queue
 * to connect that cluster to. By default, the <code>{@value #META_DATA_QUEUE_NAME}</code> property is used. If that is
 * not present, it looks for the <code>{@value #META_DATA_CLUSTER_NAME}</code> property. Ultimately, it will fall back
 * to a
 * default, which is provided during construction.
 * <p/>
 * Alternatively, you can provide the keys of the properties you wish to use to perform the lookup. See {@link
 * #MetaDataPropertyQueueNameResolver(String, String...)}
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class MetaDataPropertyQueueNameResolver implements QueueNameResolver {

    /**
     * Key of the Cluster Meta Data property that defines the name of the AMQP Queue to read messages from.
     *
     * @see MetaDataPropertyQueueNameResolver
     */
    public static final String META_DATA_QUEUE_NAME = "AMQP.QueueName";

    /**
     * Key of the Cluster Meta Data property that defines the name of the cluster.
     *
     * @see MetaDataPropertyQueueNameResolver
     */
    public static final String META_DATA_CLUSTER_NAME = "ClusterName";

    private final String defaultQueueName;
    private final String[] metaDataProperties;

    /**
     * Initializes a MetaDataPropertyQueueNameResolver that uses the default properties (<code>{@value
     * #META_DATA_QUEUE_NAME}</code> and <code>{@value #META_DATA_CLUSTER_NAME}</code>), falling back on the given
     * <code>defaultQueueName</code> if these properties are not available.
     * <p/>
     * To customize the properties to evaluate, see {@link #MetaDataPropertyQueueNameResolver(String, String...)}.
     *
     * @param defaultQueueName The queue name when no suitable property can be found
     */
    public MetaDataPropertyQueueNameResolver(String defaultQueueName) {
        this(defaultQueueName, META_DATA_QUEUE_NAME, META_DATA_CLUSTER_NAME);
    }

    /**
     * Initializes a MetaDataPropertyQueueNameResolver that uses the given <code>metaDataProperties</code> (in the
     * order
     * provided) to find a queue name to use. If none of these properties are set, it falls back on the given
     * <code>defaultQueueName</code>.
     *
     * @param defaultQueueName   The name of the queue if none of the given Meta Data properties is present
     * @param metaDataProperties The names of the Meta Data to look for, in the order provided
     */
    public MetaDataPropertyQueueNameResolver(String defaultQueueName, String... metaDataProperties) {
        this.defaultQueueName = defaultQueueName;
        this.metaDataProperties = metaDataProperties;
    }

    @Override
    public String resolveQueueName(Cluster cluster) {
        for (String metaDataProperty : metaDataProperties) {
            String queueName = (String) cluster.getMetaData().getProperty(metaDataProperty);
            if (queueName != null) {
                return queueName;
            }
        }
        return defaultQueueName;
    }
}
