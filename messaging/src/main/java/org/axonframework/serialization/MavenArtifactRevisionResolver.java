/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.serialization;

import org.axonframework.util.MavenArtifactVersionResolver;

import java.io.IOException;

/**
 * RevisionResolver that uses Maven meta data to retrieve the application version. This application version is used
 * as event revision.
 * <p/>
 * By default, Maven stores the meta-data in a file called 'pom.properties' in the JAR files under
 * 'META-INF/maven/&lt;groupId&gt;/&lt;artifactId&gt;/'.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class MavenArtifactRevisionResolver implements RevisionResolver {

    private final String version;

    /**
     * Initialize the RevisionResolver to look for the version in the Meta Data of the artifact with given
     * {@code groupId} and {@code artifactId}.
     * <p/>
     * The class loader that loaded the MavenArtifactRevisionResolver class is used to load the artifact configuration.
     *
     * @param groupId    The groupId as defined in the pom.xml file of the module
     * @param artifactId The artifactId as defined in the pom.xml file of the module
     * @throws IOException When an exception occurs reading from the maven configuration file
     */
    public MavenArtifactRevisionResolver(String groupId, String artifactId) throws IOException {
        this(groupId, artifactId, MavenArtifactRevisionResolver.class.getClassLoader());
    }

    /**
     * Initialize the RevisionResolver to look for the version in the Meta Data of the artifact with given
     * {@code groupId} and {@code artifactId}.
     *
     * @param groupId     The groupId as defined in the pom.xml file of the module
     * @param artifactId  The artifactId as defined in the pom.xml file of the module
     * @param classLoader The class loader to load the artifact configuration with
     * @throws IOException When an exception occurs reading from the maven configuration file
     */
    public MavenArtifactRevisionResolver(String groupId, String artifactId, ClassLoader classLoader)
            throws IOException {
        MavenArtifactVersionResolver versionResolver = new MavenArtifactVersionResolver(groupId,
                                                                                        artifactId,
                                                                                        classLoader);
        version = versionResolver.get();
    }

    @Override
    public String revisionOf(Class<?> payloadType) {
        return version;
    }
}
