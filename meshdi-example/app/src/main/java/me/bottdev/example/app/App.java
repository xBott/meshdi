package me.bottdev.example.app;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.graph.GraphStatefulVersionedDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.kern.version.SemVersionParser;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.diagnostic.*;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleRestartException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStopException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleUnloadException;
import me.bottdev.meshdi.moduleit.core.*;
import me.bottdev.meshdi.moduleit.core.repository.LocalModuleRepository;
import me.bottdev.meshdi.moduleit.core.selector.ModuleSelectors;

import java.nio.file.Path;
import java.util.Set;

public class App {

    public static void main(String[] args) throws
            CandidateListException,
            ModuleStopException,
            InterruptedException,
            ModuleUnloadException, ModuleRestartException {

        String pathStr = args[0];
        if (pathStr == null) throw new IllegalArgumentException("repository path must be valid");

        StatefulDependencyResolver<String, ModuleCandidate> resolver =
                new GraphStatefulVersionedDependencyResolver<>(new KahnSorter(new SimpleCycleDetector()));

        ModuleLoadEnvironment loadEnvironment = SimpleModuleLoadEnvironment.builder()
                .apiVersion(SemVersionParser.parse("1.0.0"))
                .apiLoader(App.class.getClassLoader())
                .apiPackages(Set.of(
                        "me.bottdev.meshdi",
                        "me.bottdev.kern"
                ))
                .exportRegistry(new SimpleModuleExportRegistry())
                .build();

        ModuleClassLoaderLeakDetector leakDetector = new AsyncModuleClassLoaderLeakDetector();

        ModuleManager manager = new SimpleModuleManager(resolver, loadEnvironment, leakDetector);
        ModuleRepository repository = new LocalModuleRepository(Path.of(pathStr));

        Diagnostics<ModuleLoadDiagnostic> loadDiagnostics = manager.load(repository);
        System.out.println(loadDiagnostics);

        Diagnostics<ModuleStartDiagnostic> startDiagnostics = manager.startAll();
        System.out.println(startDiagnostics);

        Diagnostics<ModuleRestartDiagnostic> restartDiagnostics = manager.restart("root", ModuleSelectors.CASCADE).confirm();
        System.out.println(restartDiagnostics);

        Diagnostics<ModuleStopDiagnostic> stopDiagnostics = manager.stopAll();
        System.out.println(stopDiagnostics);

        ModuleUnloadResult unloadResult = manager.unloadAll();
        Diagnostics<ModuleUnloadDiagnostic> unloadDiagnostics = unloadResult.diagnostics();
        System.out.println(unloadDiagnostics);

        unloadResult.gc().thenAccept(list -> list.forEach(report -> {
            switch (report) {
                case ModuleUnloadGCReport.NotUnloaded notUnloaded -> System.out.println("Module is not unloaded: " + notUnloaded.id());
                case ModuleUnloadGCReport.Success success -> System.out.println("Module is unloaded successfully: " + success.id());
                case ModuleUnloadGCReport.Leaked leaked -> System.out.println("Module is leaked: " + leaked.id() + ", " + leaked.error().getMessage());
            }
        }));

        Thread.sleep(10_000);

    }

}
