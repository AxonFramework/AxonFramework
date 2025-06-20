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

package org.axonframework.usage.detection;

import org.axonframework.usage.api.LibraryVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 * Utility class to detect Axon Framework versions from the classpath. It scans for Maven POM properties files in the
 * classpath and extracts the Axon Framework module versions. If an error occurs during detection, it logs the error and
 * returns an empty list.
 * <p>
 * Will only detect Axon Framework modules that are part of the Axon Framework or AxonIQ ecosystem, as defined by the
 * group IDs:
 * <ul>
 *     <li>org.axonframework</li>
 *     <li>org.axonframework.extensions</li>
 *     <li>io.axoniq</li>
 *     <li>io.axoniq.console</li>
 * </ul>
 *
 * This class is not intended to be instantiated, and all methods are static.
 *
 * @author Mitchell Herrijgers
 */
public final class AxonVersionDetector {

    private static final Logger logger = LoggerFactory.getLogger(AxonVersionDetector.class);
    private static final List<String> GROUP_IDS = List.of(
            "org.axonframework",
            "org.axonframework.extensions",
            "io.axoniq",
            "io.axoniq.console"
    );

    private AxonVersionDetector() {
        // Prevent instantiation
    }

    /**
     * Detects Axon Framework modules in the classpath and returns their versions. If an error occurs during detection,
     * it logs the error and returns an empty list.
     *
     * @return A list of detected Axon Framework modules with their versions.
     */
    public static List<LibraryVersion> safeDetectAxonModules() {
        try {
            return detectAxonModules();
        } catch (Exception e) {
            logger.error("Failed to detect Axon Framework modules", e);
            return List.of();
        }
    }

    private static List<LibraryVersion> detectAxonModules() {
        List<LibraryVersion> foundVersions = new LinkedList<>();
        List<URL> urlsToCheck = new LinkedList<>();
        for (String groupId : GROUP_IDS) {
            try {
                Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
                                                   .getResources("META-INF/maven/" + groupId);
                while (resources.hasMoreElements()) {
                    urlsToCheck.add(resources.nextElement());
                }
            } catch (IOException e) {
                logger.debug("Failed to retrieve Axon Framework modules for group ID: {}", groupId, e);
            }
        }
        for (URL url : urlsToCheck) {
            try {
                if (url.getProtocol().equals("jar")) {
                    foundVersions.addAll(extractVersionFromJar(url));
                } else if (url.getProtocol().equals("file")) {
                    foundVersions.addAll(extractVersionFromDirectory(url));
                }
            } catch (Exception e) {
                logger.debug("Failed to read Axon Framework module at URL: {}", url, e);
            }
        }
        return foundVersions;
    }

    private static List<LibraryVersion> extractVersionFromJar(URL url) throws IOException {
        String jarFilePath = url.getPath().substring(5, url.getPath().indexOf("!"));
        try (JarFile jarFile = new JarFile(new File(jarFilePath))) {
            return jarFile.stream()
                          .filter(entry -> entry.getName().startsWith("META-INF/maven/"))
                          .filter(entry -> entry.getName().endsWith("/pom.properties"))
                          .map(entry -> {
                              try (InputStream inputStream = jarFile.getInputStream(entry)) {
                                  return mapToAxonVersion(inputStream);
                              } catch (IOException e) {
                                  logger.debug("Failed to read pom.properties from JAR entry: {}", entry.getName(), e);
                                  return null;
                              }
                          })
                          .toList();
        }
    }

    private static LibraryVersion mapToAxonVersion(InputStream inputStream) throws IOException {
        Properties mavenProps = new Properties();
        mavenProps.load(inputStream);
        return new LibraryVersion(
                mavenProps.getProperty("groupId"),
                mavenProps.getProperty("artifactId"),
                mavenProps.getProperty("version")
        );
    }

    private static List<LibraryVersion> extractVersionFromDirectory(URL url) throws URISyntaxException, IOException {
        File file = new File(url.toURI());
        List<LibraryVersion> foundVersions = new LinkedList<>();
        if (file.isDirectory()) {
            List<File> filesToCheck = scanDirectoryRecursivelyForPomProperties(file);
            for (File pomFile : filesToCheck) {
                try {
                    Properties mavenProps = new Properties();
                    mavenProps.load(Files.newInputStream(pomFile.toPath()));
                    foundVersions.add(new LibraryVersion(
                            mavenProps.getProperty("groupId"),
                            mavenProps.getProperty("artifactId"),
                            mavenProps.getProperty("version")
                    ));
                } catch (Exception e) {
                    logger.debug("Unexpected error while processing pom.properties file: {}",
                                 pomFile.getAbsolutePath(), e);
                }
            }
        }
        return foundVersions;
    }

    private static List<File> scanDirectoryRecursivelyForPomProperties(File dir) {
        List<File> files = new LinkedList<>();
        scanDirectoryRecursivelyForPomProperties(dir, files);
        return files;
    }

    private static void scanDirectoryRecursivelyForPomProperties(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectoryRecursivelyForPomProperties(child, files);
            } else if (child.getName().equals("pom.properties")) {
                files.add(child);
            }
        }
    }
}
