package org.axonframework.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.axonframework.common.io.IOUtils.closeQuietly;

/**
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class MavenArtifactVersionResolver {

    private final String groupId;

    private final String artifactId;

    private final ClassLoader classLoader;

    public MavenArtifactVersionResolver(String groupId, String artifactId, ClassLoader classLoader) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classLoader = classLoader;
    }

    public String get() throws IOException {

        final InputStream propFile = classLoader.getResourceAsStream(
                "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties");

        if (propFile != null) {
            try {
                Properties mavenProps = new Properties();
                mavenProps.load(propFile);
                return mavenProps.getProperty("version");
            } catch (IOException e) {
                return null;
            } finally {
                closeQuietly(propFile);
            }
        } else {
            return null;
        }
    }
}
