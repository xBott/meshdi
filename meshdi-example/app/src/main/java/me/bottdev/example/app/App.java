package me.bottdev.example.app;

import me.bottdev.kern.commons.diagnostic.Diagnostics;
import me.bottdev.kern.dependency.StatefulDependencyResolver;
import me.bottdev.kern.dependency.graph.GraphStatefulVersionedDependencyResolver;
import me.bottdev.kern.struct.algorithms.cycle.SimpleCycleDetector;
import me.bottdev.kern.struct.algorithms.sort.KahnSorter;
import me.bottdev.kern.version.SemVersionParser;
import me.bottdev.meshdi.moduleit.api.*;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleStartException;
import me.bottdev.meshdi.moduleit.core.SimpleModuleExportRegistry;
import me.bottdev.meshdi.moduleit.core.SimpleModuleLoadEnvironment;
import me.bottdev.meshdi.moduleit.core.SimpleModuleManager;
import me.bottdev.meshdi.moduleit.core.repository.LocalModuleRepository;

import java.nio.file.Path;
import java.util.Set;

public class App {

    public static void main(String[] args) throws CandidateListException, ModuleStartException {

        String pathStr = args[0];
        if (pathStr == null) throw new IllegalArgumentException("repository path must be valid");

        StatefulDependencyResolver<String, ModuleCandidate> resolver =
                new GraphStatefulVersionedDependencyResolver<>(new KahnSorter(new SimpleCycleDetector()));
        ModuleLoadEnvironment loadEnvironment = new SimpleModuleLoadEnvironment(
                SemVersionParser.parse("1.0.0"),
                App.class.getClassLoader(),
                Set.of(
                        "me.bottdev.meshdi",
                        "me.bottdev.kern"
                ),
                new SimpleModuleExportRegistry()
        );

        ModuleManager manager = new SimpleModuleManager(resolver, loadEnvironment);
        ModuleRepository repository = new LocalModuleRepository(Path.of(pathStr));

        Diagnostics<ModuleDiagnostic> diagnostics = manager.load(repository);
        System.out.println(diagnostics);

        manager.start("root");
        manager.start("tool");

    }

}
