package me.bottdev.meshdi.moduleit.api;

import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleDescriptorException;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.semver4j.Semver;
import org.semver4j.range.RangeList;
import org.semver4j.range.RangeListFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/// Reads and writes the module descriptor file ([ModuleDescriptor#DESCRIPTOR_FILE]).
///
/// The descriptor is **data**: a `java.util.Properties` document. Reading it never loads a class
/// from the module jar, so listing module candidates cannot execute module code.
///
/// ```properties
/// format=1
/// id=tool
/// version=1.2.0
/// apiVersion=>=1.0.0
/// exports.0=com.example.tool.api
/// dependencies.0.id=root
/// dependencies.0.version=>=1.0.0
/// dependencies.0.link=REQUIRED
/// dependencies.0.order=AFTER
/// repositories.0.id=company
/// repositories.0.url=https://repo.example.com/releases
/// libraries.0.coordinate=com.google.code.gson:gson:2.11.0
/// libraries.0.scope=ISOLATED
/// ```
public final class ModuleDescriptorCodec {

    /// Version of the descriptor layout written by [#encode(ModuleDescriptor)].
    public static final int FORMAT = 1;

    /// Upper bound for the descriptor size. A descriptor is a few hundred bytes;
    /// anything bigger is not a descriptor and is not read into memory.
    public static final int MAX_SIZE_BYTES = 256 * 1024;

    private static final int MAX_ENTRIES = 4096;

    private ModuleDescriptorCodec() {}

    /// Encodes a descriptor object. The api version range is written as `RangeList.toString()` renders it;
    /// use the overload with explicit strings to keep the range exactly as it was declared.
    ///
    /// @return the descriptor as a properties document.
    public static String encode(ModuleDescriptor descriptor) {
        return encode(
                descriptor.id(),
                descriptor.version(),
                descriptor.apiVersion().toString(),
                descriptor.exports(),
                descriptor.getVersionedDependencies(),
                descriptor.repositories(),
                descriptor.libraries()
        );
    }

    /// @return the descriptor as a properties document with stable key order and no timestamp,
    /// so the same module always produces the same bytes.
    public static String encode(
            String id,
            String version,
            String apiVersionRange,
            Collection<String> exports,
            Collection<VersionedDependencyRequest<String>> dependencies,
            Collection<RepositoryDeclaration> repositories,
            Collection<LibraryRequirement> libraries
    ) {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("format", String.valueOf(FORMAT));
        values.put("id", id);
        values.put("version", version);
        values.put("apiVersion", apiVersionRange);

        int index = 0;
        for (String exported : exports) {
            values.put("exports." + index++, exported);
        }
        index = 0;
        for (VersionedDependencyRequest<String> dependency : dependencies) {
            String prefix = "dependencies." + index++ + ".";
            values.put(prefix + "id", dependency.key());
            values.put(prefix + "version", dependency.versionRange());
            values.put(prefix + "link", dependency.link().name());
            values.put(prefix + "order", dependency.order().name());
        }
        index = 0;
        for (RepositoryDeclaration repository : repositories) {
            String prefix = "repositories." + index++ + ".";
            values.put(prefix + "id", repository.id());
            values.put(prefix + "url", repository.url());
        }
        index = 0;
        for (LibraryRequirement library : libraries) {
            String prefix = "libraries." + index++ + ".";
            values.put(prefix + "coordinate", library.coordinate());
            values.put(prefix + "scope", library.scope().name());
        }

        // Properties handles escaping; the comment line with the current time is dropped.
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            Properties single = new Properties();
            single.setProperty(key, value);
            StringWriter writer = new StringWriter();
            try {
                single.store(writer, null);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            writer.toString().lines()
                    .filter(line -> !line.startsWith("#"))
                    .forEach(line -> out.append(line).append('\n'));
        });
        return out.toString();
    }

    /// @throws ModuleDescriptorException if the content is not a valid descriptor.
    public static ModuleDescriptor decode(String content) {
        return decode(new StringReader(content));
    }

    /// Parses a descriptor. Only data is read: no class named in the content is ever loaded.
    ///
    /// @throws ModuleDescriptorException if the content is not a valid descriptor.
    public static ModuleDescriptor decode(Reader reader) {
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ModuleDescriptorException("Module descriptor is not a valid properties document.", ex);
        }
        if (properties.size() > MAX_ENTRIES)
            throw new ModuleDescriptorException("Module descriptor has too many entries: " + properties.size());

        String format = properties.getProperty("format");
        if (format == null)
            throw new ModuleDescriptorException(
                    "Module descriptor has no 'format' entry. Descriptors that name a class are no longer "
                            + "supported: rebuild the module with the current meshdi-moduleit processor."
            );
        if (!String.valueOf(FORMAT).equals(format.trim()))
            throw new ModuleDescriptorException("Unsupported module descriptor format: " + format);

        String id = required(properties, "id");

        String versionText = required(properties, "version");
        Semver version = Semver.parse(versionText);
        if (version == null)
            throw new ModuleDescriptorException("Module '" + id + "' has an invalid version: " + versionText);

        RangeList apiVersion = range(properties.getProperty("apiVersion", "*"), "apiVersion of module '" + id + "'");

        Set<String> exports = new LinkedHashSet<>();
        for (int i = 0; properties.containsKey("exports." + i); i++) {
            exports.add(required(properties, "exports." + i));
        }

        List<VersionedDependencyRequest<String>> dependencies = new ArrayList<>();
        for (int i = 0; properties.containsKey("dependencies." + i + ".id"); i++) {
            String prefix = "dependencies." + i + ".";
            String dependencyId = required(properties, prefix + "id");
            String dependencyRange = properties.getProperty(prefix + "version", "*").trim();
            range(dependencyRange, "version of dependency '" + dependencyId + "' of module '" + id + "'");
            dependencies.add(new VersionedDependencyRequest<>(
                    dependencyId,
                    enumValue(DependencyLink.class, properties.getProperty(prefix + "link", "REQUIRED"), prefix + "link"),
                    enumValue(DependOrder.class, properties.getProperty(prefix + "order", "AFTER"), prefix + "order"),
                    dependencyRange
            ));
        }

        Set<RepositoryDeclaration> repositories = new LinkedHashSet<>();
        for (int i = 0; properties.containsKey("repositories." + i + ".id"); i++) {
            String prefix = "repositories." + i + ".";
            repositories.add(new RepositoryDeclaration(
                    required(properties, prefix + "id"),
                    required(properties, prefix + "url")
            ));
        }

        Set<LibraryRequirement> libraries = new LinkedHashSet<>();
        for (int i = 0; properties.containsKey("libraries." + i + ".coordinate"); i++) {
            String prefix = "libraries." + i + ".";
            libraries.add(new LibraryRequirement(
                    required(properties, prefix + "coordinate"),
                    enumValue(LibraryScope.class, properties.getProperty(prefix + "scope", "SHARED"), prefix + "scope")
            ));
        }

        return new SimpleModuleDescriptor(id, version, apiVersion, exports, repositories, libraries, dependencies);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank())
            throw new ModuleDescriptorException("Module descriptor has no value for '" + key + "'.");
        return value.trim();
    }

    private static RangeList range(String text, String what) {
        try {
            return RangeListFactory.create(text.trim());
        } catch (Exception ex) {
            throw new ModuleDescriptorException("Invalid version range for " + what + ": " + text, ex);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String text, String key) {
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ModuleDescriptorException("Invalid value for '" + key + "': " + text, ex);
        }
    }

}
