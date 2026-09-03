package me.bottdev.example.app;

import lombok.NonNull;
import me.bottdev.kern.commons.diagnostic.Diagnostic;
import me.bottdev.kern.commons.diagnostic.DiagnosticSink;
import me.bottdev.kern.commons.diagnostic.DiagnosticSinkFactory;
import me.bottdev.kern.commons.download.DownloadManager;
import me.bottdev.kern.commons.download.ParallelDownloadManager;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.versioned.graph.GraphStatefulVersionedDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.CycleDetector;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.kern.struct.algorithms.sort.TopologicalSorter;
import me.bottdev.meshdi.api.Context;
import me.bottdev.meshdi.api.exceptions.ContextBuildException;
import me.bottdev.meshdi.api.exceptions.ContextStartException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegisterException;
import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;
import me.bottdev.meshdi.core.builders.SimpleContextBuilder;
import me.bottdev.meshdi.core.mesh.DagContextMesh;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.library.*;
import me.bottdev.meshdi.moduleit.api.library.repositories.LocalMavenCache;
import me.bottdev.meshdi.moduleit.api.library.repositories.MavenRepositoryFactory;
import me.bottdev.meshdi.moduleit.core.*;
import me.bottdev.meshdi.moduleit.core.library.RemoteMavenRepositoryFactory;
import me.bottdev.meshdi.moduleit.core.repository.CompositeModuleRepository;
import me.bottdev.meshdi.moduleit.core.repository.LocalModuleRepository;
import org.semver4j.Semver;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static me.bottdev.kern.commons.key.KeyUtils.key;

public class App {

    private static void printModuleInfoTable(ModuleManager manager) {
        List<ModuleHandle> handles = manager.getHandles();

        if (handles == null || handles.isEmpty()) {
            System.out.println("No modules found.");
            return;
        }

        // Заголовки колонок
        String idHeader = "ID";
        String stateHeader = "State";
        String versionHeader = "Version";

        // Вычисляем максимальную ширину колонок для красивого выравнивания
        int maxIdLen = idHeader.length();
        int maxStateLen = stateHeader.length();
        int maxVersionLen = versionHeader.length();

        for (ModuleHandle handle : handles) {
            maxIdLen = Math.max(maxIdLen, handle.descriptor().id().length());
            maxStateLen = Math.max(maxStateLen, String.valueOf(handle.state()).length());
            maxVersionLen = Math.max(maxVersionLen, String.valueOf(handle.descriptor().version()).length());
        }

        // Формат строки с фиксированной шириной колонок
        String format = "| %-" + maxIdLen + "s | %-" + maxStateLen + "s | %-" + maxVersionLen + "s |%n";
        String separator = "+" + "-".repeat(maxIdLen + 2) + "+" + "-".repeat(maxStateLen + 2) + "+" + "-".repeat(maxVersionLen + 2) + "+";

        // Вывод таблицы
        System.out.println(separator);
        System.out.printf(format, idHeader, stateHeader, versionHeader);
        System.out.println(separator);

        for (ModuleHandle handle : handles) {
            System.out.printf(format,
                    handle.descriptor().id(),
                    handle.state(),
                    handle.descriptor().version()
            );
        }

        System.out.println(separator);
    }

    private static Context createInternalContext() throws ContextBuildException, ContextStartException {

        Context context = new SimpleContextBuilder()
                .id("api")
                .factory(key(String.class, "name"), config -> config.factory(_ -> "APP V1"))
                .factory(key(HttpClient.class), config ->
                        config.factory(_ -> HttpClient.newBuilder().build())
                )
                .construct(key(DownloadManager.class), config ->
                        config.implementation(ParallelDownloadManager.class)
                )
                .construct(key(CycleDetector.class), config ->
                        config.implementation(SimpleCycleDetector.class)
                )
                .construct(key(TopologicalSorter.class), config ->
                        config.implementation(KahnSorter.class)
                )
                .factory(key(LocalMavenCache.class), config ->
                        config.factory(_ -> new LocalMavenCache("local", Path.of("libraries")))
                )
                .construct(key(MavenRepositoryFactory.class), config ->
                        config.implementation(RemoteMavenRepositoryFactory.class)
                )
                .factory(key(MavenRepositoryChain.class), config ->
                        config
                                .dependsOn(key(LocalMavenCache.class), DependencyLink.REQUIRED, DependOrder.AFTER)
                                .dependsOn(key(MavenRepositoryFactory.class), DependencyLink.REQUIRED, DependOrder.AFTER)
                                .factory(resolver -> {

                                    MavenRepositoryFactory repositoryFactory = resolver.get(key(MavenRepositoryFactory.class));
                                    MavenRepository mavenCentral = repositoryFactory.create(
                                            "maven-central",
                                            "https://repo.maven.apache.org/maven2/"
                                    );

                                    MavenRepository nimbraReposilite = repositoryFactory.create(
                                            "nimbra-reposilite",
                                            "https://reposlite.nimbra.net/snapshots"
                                    );
                                    LocalMavenCache localCache = resolver.get(key(LocalMavenCache.class));

                                    return new MavenRepositoryChain(List.of(localCache, mavenCentral, nimbraReposilite));
                                 })
                )
                .construct(key(PomResolver.class), config ->
                        config.implementation(PomResolver.class)
                )
                .construct(key(MavenDependencyResolver.class), config ->
                        config.implementation(MavenDependencyResolver.class)
                )
                .construct(key(MavenBatchDownloader.class), config ->
                        config.implementation(MavenBatchDownloader.class)
                )
                .factory(key(ModuleLoadEnvironment.class), config ->
                        config.factory(_ -> SimpleModuleLoadEnvironment.builder()
                                .apiVersion(Objects.requireNonNull(Semver.parse("1.0.0")))
                                .apiLoader(App.class.getClassLoader())
                                .apiPackages(Set.of(
                                        "me.bottdev.meshdi",
                                        "me.bottdev.kern"
                                ))
                                .exportRegistry(new SimpleModuleExportRegistry())
                                .diagnosticSinkFactory(new DiagnosticSinkFactory() {
                                    @Override
                                    public @NonNull <D extends Diagnostic> DiagnosticSink<D> create() {
                                        return DiagnosticSink.forwarding(System.out::println);
                                    }
                                })
                                .build())
                )
                .build();

        context.start();

        return context;
    }

    @SuppressWarnings("unchecked")
    private static ModuleManager createModuleManager(Context internalContext) throws
            MeshRegistrationBuildException,
            MeshRegisterException
    {

        return SimpleModuleManager.builder()
                .dependencyResolver(internalContext.autowire(GraphStatefulVersionedDependencyResolver.class))
                .environment(internalContext.get(ModuleLoadEnvironment.class))
                .libraryLoader(internalContext.autowire(ParallelModuleLibraryLoader.class))
                .contextMesh(new DagContextMesh())
                .leakDetector(new AsyncModuleClassLoaderLeakDetector())
                .build();

    }

    private static <T> T logAndPrint(String stepName, T diagnostics, ModuleManager manager) {
        System.out.println("=== " + stepName + " ===");
        System.out.println(diagnostics);
        printModuleInfoTable(manager);
        System.out.println();
        return diagnostics;
    }

    public static void main(String[] args) throws
            CandidateListException,
            ContextStartException,
            ContextBuildException,
            MeshRegistrationBuildException,
            MeshRegisterException
    {

        Context context = createInternalContext();
        ModuleManager manager = createModuleManager(context);

        ModuleRepository virtualRepository = new VirtualModuleRepository(
                VirtualModuleHandle.builder()
                        .descriptor(
                                VirtualModuleDescriptor.builder()
                                        .id("api")
                                        .semver(Objects.requireNonNull(Semver.parse("1.0.0")))
                                        .build()
                        )
                        .context(context)
                        .persistent(true)
                        .disposeOnStop(true)
                        .trackClassLoaderOnUnload(false)
                        .build()
        );
        ModuleRepository localRepository = new LocalModuleRepository(Path.of("modules"));
        ModuleRepository compositeRepository = new CompositeModuleRepository(virtualRepository, localRepository);

        ModuleBatchResult resolutionResult = manager.resolve(compositeRepository);
        System.out.println("Resolution: " + resolutionResult);
        printModuleInfoTable(manager);

        manager.prepareAll()
                .thenApply(diag -> logAndPrint("PREPARE", diag, manager))
                .thenApply(_ -> logAndPrint("LOAD", manager.loadAll(), manager))
                .thenApply(_ -> logAndPrint("START", manager.startAll(), manager))
                .thenApply(_ -> logAndPrint("STOP", manager.stopAll(), manager))
                .thenCompose(_ -> manager.unloadAll())
                .thenAccept(diag -> logAndPrint("UNLOAD", diag, manager))
                .join();
    }

}
