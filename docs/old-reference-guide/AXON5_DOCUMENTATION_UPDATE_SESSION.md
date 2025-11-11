
---

# Session 3 (Continued) - Serializer → Converter Migration

## Work Completed

### ✅ Complete Removal of Serializer Concept - Replaced with Converter

**Objective**: Remove the Serializer concept entirely and replace it with the Converter concept throughout all documentation.

**Major Changes:**

1. **XStreamSerializer Removal**
   - Removed all references to XStreamSerializer (no longer supported in Axon 5)
   - Removed XStream-specific configuration examples
   - Removed XStream tuning section from serialization.adoc

2. **Terminology Updates**
   - `Serializer` → `Converter` (all occurrences)
   - `JacksonSerializer` → `JacksonConverter`
   - `AvroSerializer` → `AvroConverter`
   - `serialization` → `conversion`
   - `serialize/deserialize` → `convert/convert from`

3. **Configuration Model Changes**
   - Removed three-tier serializer configuration (default/message/event)
   - Updated to single converter configuration
   - `configureSerializer()` → `configureMessageConverter()`
   - No more separate converters for events vs messages

4. **Default Behavior**
   - **Axon 4**: XStreamSerializer used by default
   - **Axon 5**: No conversion by default (passthrough)
   - Spring Boot auto-configures JacksonConverter when Jackson is on classpath

**Files Modified:** 35+ .adoc files across the documentation

**Major File Rewrites:**

1. **serialization.adoc** → **Now titled "Conversion"**
   - Complete rewrite from 362 lines
   - Removed all XStream content
   - Updated to focus on JacksonConverter and AvroConverter
   - Added PassThroughConverter documentation
   - Updated configuration examples
   - Added migration guide from Axon 4

---

## Key Conceptual Changes

### Converter vs Serializer

**In Axon 4:**
- "Serializer" implied converting to/from persistent formats
- Three separate serializers: default, message, event
- XStreamSerializer was default for everything
- Focus on storage format

**In Axon 5:**
- "Converter" is more generic - changes technical representation
- Single converter for all message types
- No default converter (passthrough unless configured)
- Not tied specifically to messaging infrastructure
- Can be used for any data conversion needs

### Default Behavior

**Axon 4:**
```java
// XStreamSerializer used by default
// Everything converted to XML automatically
```

**Axon 5:**
```java
// No conversion by default
// Messages stay as Java objects unless converter configured

// Spring Boot with Jackson on classpath:
// Automatically uses JacksonConverter
```

### Configuration Simplification

**Axon 4:**
```java
configurer.configureSerializer(config -> xStream)          // For tokens, sagas, snapshots
          .configureMessageSerializer(config -> jackson)   // For commands/queries
          .configureEventSerializer(config -> jackson);    // For events
```

**Axon 5:**
```java
configurer.configureMessageConverter(config -> jackson);  // For everything
```

---

## Implementation Details

### JacksonConverter

- Most common converter in Axon 5
- Auto-configured by Spring Boot when Jackson on classpath
- Converts to/from JSON format
- Compact, human-readable, widely supported
- Recommended for event storage

### AvroConverter

- Schema-based binary conversion
- Requires SchemaStore for schema resolution
- Requires delegate converter (typically JacksonConverter) for metadata
- Ideal for enforced schema evolution
- Compact binary format

### PassThroughConverter

- No conversion occurs
- Messages remain as Java objects
- Used when conversion not needed

---

## Files Modified (35 total)

**Core Documentation:**
- ROOT/pages/serialization.adoc (renamed conceptually to "Conversion")
- ROOT/pages/index.adoc
- ROOT/pages/known-issues-and-workarounds.adoc

**Command Framework:**
- axon-framework-commands/pages/configuration.adoc
- axon-framework-commands/pages/infrastructure.adoc

**Events:**
- events/pages/infrastructure.adoc
- events/pages/event-versioning.adoc
- events/pages/event-processors/dead-letter-queue.adoc
- events/pages/event-processors/subscribing.adoc

**Sagas:**
- sagas/pages/implementation.adoc
- sagas/pages/infrastructure.adoc

**Deadlines:**
- deadlines/pages/deadline-managers.adoc
- deadlines/pages/event-schedulers.adoc

**Testing, Tuning, Monitoring:**
- tuning/pages/event-snapshots.adoc
- tuning/pages/rdbms-tuning.adoc

**Release Notes:**
- release-notes/pages/major-releases.adoc
- release-notes/pages/minor-releases.adoc

**Messaging Concepts:**
- messaging-concepts/pages/anatomy-message.adoc
- messaging-concepts/pages/index.adoc

**Navigation:**
- nav.adoc

**And 15+ other files with Serializer references**

---

## Summary Statistics (Session 3 - Serializer → Converter)

- **Files Modified**: 35+ .adoc files
- **Major Rewrites**: 1 (serialization.adoc)
- **Terminology Changes**: 200+ occurrences
- **XStream References Removed**: All occurrences
- **Configuration Examples Updated**: 15+

---

## Verification Points

Before publishing, verify:
- [ ] All code examples compile with Axon 5
- [ ] No remaining XStreamSerializer references
- [ ] No references to three-tier serializer configuration
- [ ] JacksonConverter used in examples (not JacksonSerializer)
- [ ] AvroConverter used in examples (not AvroSerializer)
- [ ] Configuration examples use `configureMessageConverter()`


---

### ✅ Configuration API Correction

**Correction Made**: The initial documentation incorrectly referenced a `Configurer` class and `configureMessageConverter()` method that don't exist in Axon 5.

**Correct Configuration Approaches:**

1. **Spring Boot (Recommended)**: Simply define a `Converter` bean
```java
@Bean
public Converter converter() {
    return new JacksonConverter();
}
```

2. **Programmatic (ComponentRegistry)**: Register through ComponentRegistry
```java
MessagingConfigurer.create()
    .componentRegistry(registry -> {
        registry.registerComponent(Converter.class, config -> new JacksonConverter());
    });
```

3. **Spring Boot Properties**:
```properties
axon.converter.general=jackson
axon.converter.messages=jackson  # Optional override
axon.converter.events=jackson    # Optional override
```

**Auto-Configuration**: When using Spring Boot with Jackson on the classpath, the `ConverterAutoConfiguration` automatically configures a `JacksonConverter` - no configuration needed.

