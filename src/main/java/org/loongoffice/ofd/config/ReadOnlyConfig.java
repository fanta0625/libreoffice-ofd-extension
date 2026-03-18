package org.loongoffice.ofd.config;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration for read-only properties applied to OFD documents.
 * Centralizes property names and default values to make them easier to modify.
 * Default values can be overridden by placing a readonly.properties file in the classpath.
 */
public class ReadOnlyConfig {

    /**
     * Property names for read-only protection.
     * These properties are applied when loading OFD files to enforce read-only mode.
     */
    public static final String[] READ_ONLY_PROPERTY_NAMES = {
        "ReadOnly",
        "LockContentExtraction",
        "LockExport",
        "LockPrint",
        "LockEditDoc",
        "LockSave"
    };

    /**
     * Default values for each property.
     * Most properties default to true, except LockPrint which defaults to false.
     * The map uses property name as key and Boolean value as default.
     * Loaded from readonly.properties if available, otherwise uses hardcoded defaults.
     */
    private static final Map<String, Boolean> DEFAULT_VALUES = new HashMap<>();
    static {
        // Hardcoded defaults
        DEFAULT_VALUES.put("ReadOnly", true);
        DEFAULT_VALUES.put("LockContentExtraction", true);
        DEFAULT_VALUES.put("LockExport", true);
        DEFAULT_VALUES.put("LockPrint", false);  // Allow printing by default
        DEFAULT_VALUES.put("LockEditDoc", true);
        DEFAULT_VALUES.put("LockSave", true);

        // Try to load from properties file
        try (InputStream is = ReadOnlyConfig.class.getClassLoader().getResourceAsStream("readonly.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                for (String propName : DEFAULT_VALUES.keySet()) {
                    String value = props.getProperty(propName);
                    if (value != null) {
                        boolean boolValue = Boolean.parseBoolean(value.trim());
                        DEFAULT_VALUES.put(propName, boolValue);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore, use hardcoded defaults
        }
    }

    /**
     * Gets the default value for a property.
     * @param propertyName the property name
     * @return the default boolean value, or true if property not found (fallback)
     */
    public static boolean getDefaultValue(String propertyName) {
        return DEFAULT_VALUES.getOrDefault(propertyName, true);
    }

    /**
     * Creates a PropertyValue array with all read-only properties set to their defaults.
     * This is useful for creating load properties when loading a new document.
     * @return PropertyValue array with all read-only properties
     */
    public static com.sun.star.beans.PropertyValue[] createReadOnlyProperties() {
        com.sun.star.beans.PropertyValue[] props = new com.sun.star.beans.PropertyValue[READ_ONLY_PROPERTY_NAMES.length];
        for (int i = 0; i < READ_ONLY_PROPERTY_NAMES.length; i++) {
            props[i] = new com.sun.star.beans.PropertyValue();
            props[i].Name = READ_ONLY_PROPERTY_NAMES[i];
            props[i].Value = getDefaultValue(READ_ONLY_PROPERTY_NAMES[i]);
        }
        return props;
    }

    /**
     * Adds read-only properties to an existing PropertyValue array.
     * If the property already exists, it will be overwritten with the default value.
     * @param original the original PropertyValue array (may be null)
     * @return new PropertyValue array with read-only properties added
     */
    public static com.sun.star.beans.PropertyValue[] addReadOnlyProperties(com.sun.star.beans.PropertyValue[] original) {
        int originalLength = original != null ? original.length : 0;
        com.sun.star.beans.PropertyValue[] newProps = new com.sun.star.beans.PropertyValue[originalLength + READ_ONLY_PROPERTY_NAMES.length];

        if (original != null) {
            System.arraycopy(original, 0, newProps, 0, originalLength);
        }

        for (int i = 0; i < READ_ONLY_PROPERTY_NAMES.length; i++) {
            com.sun.star.beans.PropertyValue pv = new com.sun.star.beans.PropertyValue();
            pv.Name = READ_ONLY_PROPERTY_NAMES[i];
            pv.Value = getDefaultValue(READ_ONLY_PROPERTY_NAMES[i]);
            newProps[originalLength + i] = pv;
        }

        return newProps;
    }

    /**
     * Applies read-only properties to an existing document by setting each property to its default value.
     * @param doc the document component
     * @param logger a consumer for logging messages (can be null)
     */
    public static void applyReadOnlyProperties(com.sun.star.lang.XComponent doc, java.util.function.Consumer<String> logger) {
        if (doc == null) return;

        try {
            com.sun.star.beans.XPropertySet propSet = com.sun.star.uno.UnoRuntime.queryInterface(
                com.sun.star.beans.XPropertySet.class, doc);
            if (propSet != null) {
                for (String propName : READ_ONLY_PROPERTY_NAMES) {
                    try {
                        boolean defaultValue = getDefaultValue(propName);
                        propSet.setPropertyValue(propName, defaultValue);
                        if (logger != null) {
                            logger.accept("Set property " + propName + " = " + defaultValue);
                        }
                    } catch (Exception e) {
                        if (logger != null) {
                            logger.accept("Failed to set property " + propName + ": " + e);
                        }
                    }
                }
            } else {
                if (logger != null) {
                    logger.accept("Cannot get XPropertySet from document");
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.accept("Exception in applyReadOnlyProperties: " + e);
            }
        }
    }
}