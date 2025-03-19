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

package org.axonframework.common.infra;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * A {@link ComponentDescriptor} implementation inspired by filesystem structures. Components are represented as
 * "directories" with properties as "files" and references as "symbolic links". This creates a clear hierarchical
 * representation with explicit paths to show component relationships.
 * <p>
 * Each component is assigned a path in the virtual filesystem. References to components that have already been
 * described are represented as symbolic links, indicated with the {@code -> /path/to/component} syntax, preventing
 * infinite recursion in circular references.
 * <p>
 * Example output:
 * <pre>
 * /
 * ├── systemComponent/
 * │   ├── _type: EventProcessor
 * │   ├── name: my-processor
 * │   └── configuration/
 * │       ├── _type: ProcessorConfiguration
 * │       ├── batchSize: 100
 * │       └── processor -> /systemComponent
 * </pre>
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class FilesystemComponentDescriptor implements ComponentDescriptor {

    private static final String ROOT_PATH = "/";
    private static final String PATH_SEPARATOR = "/";
    private static final String SYMLINK_INDICATOR = " -> ";

    private final Map<Object, String> componentPaths;
    private final Map<String, Object> properties;
    private final String currentPath;

    /**
     * Constructs a new {@code FilesystemComponentDescriptor} as the root of the hierarchy.
     */
    public FilesystemComponentDescriptor() {
        this(new IdentityHashMap<>(), ROOT_PATH);
    }

    /**
     * Private constructor used for creating nested descriptors that share the component paths map.
     *
     * @param componentPaths Map containing paths to already processed components
     * @param currentPath    The current path in the virtual filesystem
     */
    private FilesystemComponentDescriptor(Map<Object, String> componentPaths, String currentPath) {
        this.componentPaths = componentPaths;
        this.currentPath = currentPath;
        this.properties = new LinkedHashMap<>(); // Use LinkedHashMap to maintain property order
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Object object) {
        if (object instanceof DescribableComponent component) {
            describeComponent(name, component);
        } else {
            // For non-DescribableComponent objects, just store the value
            properties.put(name, object);
        }
    }

    private void describeComponent(String name, DescribableComponent component) {
        // Create the path for this component
        String componentPath = createPath(name);

        // Check if we've already processed this component
        if (componentPaths.containsKey(component)) {
            // Create a symbolic link to the existing component
            String existingPath = componentPaths.get(component);
            properties.put(name, new SymbolicLink(existingPath));
            return;
        }

        // Register this component's path before processing to handle circular references
        componentPaths.put(component, componentPath);

        // Create a nested descriptor for this component
        FilesystemComponentDescriptor childDescriptor =
                new FilesystemComponentDescriptor(componentPaths, componentPath);

        // Add type information
        childDescriptor.properties.put("_type", component.getClass().getSimpleName());

        // Let the component describe itself
        component.describeTo(childDescriptor);

        // Add the component as a child node
        properties.put(name, childDescriptor);
    }

    private String createPath(String name) {
        if (currentPath.equals(ROOT_PATH)) {
            return currentPath + name;
        }
        return currentPath + PATH_SEPARATOR + name;
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Collection<?> collection) {
        List<Object> items = new ArrayList<>();

        int index = 0;
        for (Object item : collection) {
            if (item instanceof DescribableComponent component) {
                // For components in a collection, we use indexed paths
                String itemName = name + "[" + index + "]";
                String itemPath = createPath(itemName);

                // Check if this component has already been processed
                if (componentPaths.containsKey(component)) {
                    // Create a reference to the existing component
                    String existingPath = componentPaths.get(component);
                    items.add(new SymbolicLink(existingPath));
                } else {
                    // Process a new component
                    componentPaths.put(component, itemPath);

                    FilesystemComponentDescriptor childDescriptor =
                            new FilesystemComponentDescriptor(componentPaths, itemPath);

                    childDescriptor.properties.put("_type", component.getClass().getSimpleName());
                    component.describeTo(childDescriptor);
                    items.add(childDescriptor);
                }
            } else {
                // For non-component items, just add the value
                items.add(item);
            }
            index++;
        }

        properties.put(name, items);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull Map<?, ?> map) {
        Map<String, Object> mappedItems = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();

            if (value instanceof DescribableComponent component) {
                // For components in a map, we use key-based paths
                String itemName = name + "[" + key + "]";
                String itemPath = createPath(itemName);

                // Check if this component has already been processed
                if (componentPaths.containsKey(component)) {
                    // Create a reference to the existing component
                    String existingPath = componentPaths.get(component);
                    mappedItems.put(key, new SymbolicLink(existingPath));
                } else {
                    // Process a new component
                    componentPaths.put(component, itemPath);

                    FilesystemComponentDescriptor childDescriptor =
                            new FilesystemComponentDescriptor(componentPaths, itemPath);

                    childDescriptor.properties.put("_type", component.getClass().getSimpleName());
                    component.describeTo(childDescriptor);
                    mappedItems.put(key, childDescriptor);
                }
            } else {
                // For non-component values, just add the value
                mappedItems.put(key, value);
            }
        }

        properties.put(name, mappedItems);
    }

    @Override
    public void describeProperty(@Nonnull String name, @Nonnull String value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, long value) {
        properties.put(name, value);
    }

    @Override
    public void describeProperty(@Nonnull String name, boolean value) {
        properties.put(name, value);
    }

    @Override
    public String describe() {
        StringBuilder result = new StringBuilder();
        result.append(ROOT_PATH).append("\n");

        // Render the property tree starting from the root
        renderProperties(result, properties, "", "");

        return result.toString();
    }

    /**
     * Recursively renders the properties as a filesystem-like tree structure.
     *
     * @param result The StringBuilder to append the result to
     * @param props  The properties to render
     * @param indent The current indentation string
     * @param path   The current path in the tree
     */
    private void renderProperties(StringBuilder result, Map<String, Object> props, String indent, String path) {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(props.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            String name = entry.getKey();
            Object value = entry.getValue();

            boolean isLast = (i == entries.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String childIndent = indent + (isLast ? "    " : "│   ");

            if (value instanceof FilesystemComponentDescriptor descriptor) {
                // Render a directory for nested components
                result.append(indent).append(connector).append(name).append("/\n");
                renderProperties(result, descriptor.properties, childIndent, path + "/" + name);
            } else if (value instanceof List<?> list) {
                // Render a collection as a directory with numbered items
                result.append(indent).append(connector).append(name).append("/\n");

                for (int j = 0; j < list.size(); j++) {
                    Object item = list.get(j);
                    boolean isLastItem = (j == list.size() - 1);
                    String itemConnector = isLastItem ? "└── " : "├── ";
                    String itemIndent = childIndent + (isLastItem ? "    " : "│   ");
                    String indexName = "[" + j + "]";

                    if (item instanceof FilesystemComponentDescriptor itemDescriptor) {
                        // Nested component in a list
                        result.append(childIndent).append(itemConnector).append(indexName).append("/\n");
                        renderProperties(result,
                                         itemDescriptor.properties,
                                         itemIndent,
                                         path + "/" + name + "/" + indexName);
                    } else if (item instanceof SymbolicLink link) {
                        // Reference to another component
                        result.append(childIndent).append(itemConnector).append(indexName)
                              .append(SYMLINK_INDICATOR).append(link.targetPath).append("\n");
                    } else {
                        // Simple value in a list
                        result.append(childIndent).append(itemConnector).append(indexName)
                              .append(": ").append(formatValue(item)).append("\n");
                    }
                }
            } else if (value instanceof Map<?, ?> map) {
                // Render a map as a directory with named entries
                result.append(indent).append(connector).append(name).append("/\n");

                List<Map.Entry<?, ?>> mapEntries = new ArrayList<>(map.entrySet());
                for (int j = 0; j < mapEntries.size(); j++) {
                    Map.Entry<?, ?> mapEntry = mapEntries.get(j);
                    String key = mapEntry.getKey().toString();
                    Object mapValue = mapEntry.getValue();
                    boolean isLastMapEntry = (j == mapEntries.size() - 1);
                    String mapConnector = isLastMapEntry ? "└── " : "├── ";
                    String mapIndent = childIndent + (isLastMapEntry ? "    " : "│   ");

                    if (mapValue instanceof FilesystemComponentDescriptor mapDescriptor) {
                        // Nested component in a map
                        result.append(childIndent).append(mapConnector).append(key).append("/\n");
                        renderProperties(result, mapDescriptor.properties, mapIndent, path + "/" + name + "/" + key);
                    } else if (mapValue instanceof SymbolicLink link) {
                        // Reference to another component
                        result.append(childIndent).append(mapConnector).append(key)
                              .append(SYMLINK_INDICATOR).append(link.targetPath).append("\n");
                    } else {
                        // Simple value in a map
                        result.append(childIndent).append(mapConnector).append(key)
                              .append(": ").append(formatValue(mapValue)).append("\n");
                    }
                }
            } else if (value instanceof SymbolicLink link) {
                // Direct symbolic link
                result.append(indent).append(connector).append(name)
                      .append(SYMLINK_INDICATOR).append(link.targetPath).append("\n");
            } else {
                // Simple property
                result.append(indent).append(connector).append(name)
                      .append(": ").append(formatValue(value)).append("\n");
            }
        }
    }

    /**
     * Formats a value for display in the tree structure.
     *
     * @param value The value to format
     * @return A string representation of the value
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        return value.toString();
    }

    /**
     * Helper class to represent symbolic links in the virtual filesystem.
     */
    private static class SymbolicLink {

        private final String targetPath;

        public SymbolicLink(String targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public String toString() {
            return SYMLINK_INDICATOR + targetPath;
        }
    }
}