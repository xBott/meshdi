package me.bottdev.meshdi.moduleit.processor;

import com.google.auto.service.AutoService;
import lombok.Builder;
import lombok.NonNull;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.VersionedDependencyRequest;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleDescriptorCodec;
import me.bottdev.meshdi.moduleit.api.annotations.Module;
import me.bottdev.kern.meta.apt.AbstractMetaProcessor;
import me.bottdev.kern.meta.core.FileWriter;
import me.bottdev.kern.meta.core.configuration.ProcessorConfigurationBuilder;
import me.bottdev.kern.meta.core.models.ModelKind;
import me.bottdev.kern.meta.core.models.type.ClassModel;
import me.bottdev.meshdi.moduleit.api.library.LibraryRequirement;
import me.bottdev.meshdi.moduleit.api.library.LibraryScope;
import me.bottdev.meshdi.moduleit.api.library.RepositoryDeclaration;
import org.semver4j.Semver;
import org.semver4j.range.RangeListFactory;

import javax.annotation.processing.Processor;

import java.util.Arrays;
import java.util.List;

import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.error;
import static me.bottdev.kern.meta.core.diagnostic.DiagnosticRuleResult.ok;

@AutoService(Processor.class)
public class ModuleMetaProcessor extends AbstractMetaProcessor {


    private record DependencyRepresentation(
            String id,
            DependencyLink link,
            DependOrder order,
            String version
    ) {}

    private record RepositoryRepresentation(
            @NonNull String id,
            @NonNull String url
    ) {}

    private record LibraryRepresentation(
            @NonNull String coordinate,
            @NonNull LibraryScope scope
    ) {}

    @Builder
    private record ModuleRepresentation(
            @NonNull ClassModel model,
            @NonNull String id,
            @NonNull String version,
            @NonNull String apiVersion,
            @NonNull List<DependencyRepresentation> dependencies,
            @NonNull String[] exports,
            @NonNull List<RepositoryRepresentation> repositories,
            @NonNull List<LibraryRepresentation> libraries
    ) {}

    private ModuleRepresentation discovered = null;

    /// Module repositories must be `https`: over plain `http` an artifact and its checksum
    /// can both be replaced in transit.
    private boolean isValidUrl(String urlString) {
        return RepositoryDeclaration.isSecureUrl(urlString);
    }

    private boolean isValidMavenCoordinate(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) {
            return false;
        }

        String[] parts = coordinate.split(":");

        if (parts.length < 3 || parts.length > 5) {
            return false;
        }

        String partRegex = "^[A-Za-z0-9_\\-.]+$";

        for (String part : parts) {
            if (part == null || part.trim().isEmpty() || !part.matches(partRegex)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected void configure(ProcessorConfigurationBuilder builder) {

        builder.select(ModelKind.CLASS, Module.class)
                .map(model -> {
                    Module module = model.annotation(Module.class).orElseThrow();
                    String id = module.id();
                    String version = module.version();
                    String apiVersion = module.apiVersion();

                    List<DependencyRepresentation> dependencies = Arrays.stream(module.dependencies())
                            .map(dependsOn -> new DependencyRepresentation(
                                    dependsOn.id(),
                                    dependsOn.link(),
                                    dependsOn.order(),
                                    dependsOn.version()
                                    )
                            ).toList();

                    String[] exports = module.exports();

                    List<RepositoryRepresentation> repositories = Arrays.stream(module.repositories())
                            .map(repository -> new RepositoryRepresentation(repository.id(), repository.url()))
                            .toList();

                    List<LibraryRepresentation> libraries = Arrays.stream(module.libraries())
                            .map(library -> new LibraryRepresentation(library.value(), library.scope()))
                            .toList();

                    return ModuleRepresentation.builder()
                            .model(model)
                            .id(id)
                            .version(version)
                            .apiVersion(apiVersion)
                            .dependencies(dependencies)
                            .exports(exports)
                            .repositories(repositories)
                            .libraries(libraries)
                            .build();

                })
                .validate(rules -> rules
                        .rule(_ -> discovered == null ? ok() : error("Project can contain only one module."))
                        .rule(representation -> {
                            try {
                                Semver.parse(representation.version());
                                return ok();

                            } catch (Exception ex) {
                                return error(
                                        "Module version has incorrect format: " +
                                                representation.version() +
                                                ". Follow SemVer format: "
                                );

                            }
                        })
                        .rule(representation -> {
                            try {
                                RangeListFactory.create(representation.apiVersion());
                                return ok();

                            } catch (Exception ex) {
                                return error(
                                        "Module api version range has incorrect format: " +
                                                representation.apiVersion() +
                                                ". Follow SemVer format: "
                                );

                            }
                        })
                        .rule(representation -> {
                            for (DependencyRepresentation dependency : representation.dependencies()) {
                                try {
                                    RangeListFactory.create(dependency.version());

                                } catch (Exception ex) {
                                    return error(
                                            "Version range of module's dependency \"" +
                                                    dependency.id() +
                                                    "\" has incorrect format: " +
                                                    dependency.version() +
                                                    ". Follow SemVer format."
                                    );

                                }
                            }
                            return ok();
                        })
                        .rule(representation -> {
                            for (RepositoryRepresentation repository : representation.repositories()) {
                                if (!isValidUrl(repository.url())) return error(
                                        "Declared repository \"" +
                                                repository.id() +
                                                "\" must have a valid https URL, but got: " +
                                                repository.url()
                                );
                            }
                            return ok();
                        })
                        .rule(representation -> {
                            for (LibraryRepresentation library : representation.libraries) {
                                if (!isValidMavenCoordinate(library.coordinate)) return error(
                                        "Required library has incorrect maven coordinate format: \"" +
                                                library.coordinate()
                                );
                            }
                            return ok();
                        })
                )
                .run(representation -> discovered = representation);

        builder.afterAll()
                .generate(() -> {
                    // Nothing to describe when the compilation unit has no @Module class.
                    if (discovered == null) return;
                    generateDescriptorFile(discovered);
                });

    }

    /// Writes the descriptor as data. The host parses it without loading any class of the module.
    private void generateDescriptorFile(ModuleRepresentation representation) {

        String path = ModuleDescriptor.DESCRIPTOR_FILE;

        String content = ModuleDescriptorCodec.encode(
                representation.id(),
                representation.version(),
                representation.apiVersion(),
                List.of(representation.exports()),
                representation.dependencies().stream()
                        .map(dependency -> new VersionedDependencyRequest<>(
                                dependency.id(),
                                dependency.link(),
                                dependency.order(),
                                dependency.version()
                        ))
                        .toList(),
                representation.repositories().stream()
                        .map(repository -> new RepositoryDeclaration(repository.id(), repository.url()))
                        .toList(),
                representation.libraries().stream()
                        .map(library -> new LibraryRequirement(library.coordinate(), library.scope()))
                        .toList()
        );

        try (FileWriter writer = context().fileFactory().createWriter(path)) {
            writer.open();
            writer.write(content);

        } catch (Exception ex) {
            throw new RuntimeException("failed to generate " + path + ".", ex);
        }

    }

}
