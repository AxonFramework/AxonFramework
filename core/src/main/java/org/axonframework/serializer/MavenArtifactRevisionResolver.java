package org.axonframework.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * @author Allard Buijze
 */
public class MavenArtifactRevisionResolver implements RevisionResolver {

    private final String version;

    public MavenArtifactRevisionResolver(String groupId, String artifactId) throws IOException {
        this(groupId, artifactId, MavenArtifactRevisionResolver.class.getClassLoader());
    }

    public MavenArtifactRevisionResolver(String groupId, String artifactId, ClassLoader classLoader)
            throws IOException {
        final InputStream propFile = classLoader.getResourceAsStream(
                "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");
        if (propFile != null) {
            try {
                Properties mavenProps = new Properties();
                mavenProps.load(propFile);
                version = mavenProps.getProperty("version");
            } finally {
                closeQuietly(propFile);
            }
        } else {
            version = null;
        }
    }

    @Override
    public String revisionOf(Class<?> payloadType) {
        return version;
    }
}
