package me.bottdev.meshdi.moduleit.core.repository;

import me.bottdev.meshdi.moduleit.api.ModuleCandidate;
import me.bottdev.meshdi.moduleit.api.ModuleRepository;
import me.bottdev.meshdi.moduleit.api.exceptions.CandidateListException;
import me.bottdev.meshdi.moduleit.core.utils.TestModuleJarBuilder;
import me.bottdev.meshdi.moduleit.processor.ModuleMetaProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalModuleRepositoryTest {

    @TempDir
    private Path repoDir;

    private ModuleRepository repository;

    @BeforeEach
    void setup() {
        repository = new LocalModuleRepository(repoDir);
    }

    @Test
    @DisplayName("candidates: should return a single module candidate without any errors")
    void candidates_singleSuccess() throws CandidateListException, IOException {

        TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                .withSource("com.test.ExampleModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "example", version = "0.0.1", apiVersion = "0.0.1",
                        dependencies = { @DependsOn(id = "root", version = ">0.0.1") })
                        public class ExampleModule {}
                        """
                )
                .buildTo(repoDir, "example-module-0.0.1.jar");

        List<ModuleCandidate> candidates = repository.candidates();
        assertThat(candidates)
                .hasSize(1)
                .satisfies(list -> {

                    ModuleCandidate candidate = list.getFirst();
                    assertThat(candidate.sourceKey()).isEqualTo("example-module-0.0.1.jar");

                });
    }

    @Test
    @DisplayName("candidates: should return a multiple module candidates without any errors")
    void candidates_multipleSuccess() throws CandidateListException, IOException {

        TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                .withSource("com.test.ExampleModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "example", version = "0.0.1", apiVersion = "0.0.1")
                        public class ExampleModule {}
                        """
                )
                .buildTo(repoDir, "example-module-0.0.1.jar");

        TestModuleJarBuilder.withProcessors(new ModuleMetaProcessor())
                .withSource("com.test.AnotherModule", """
                        package com.test;
                        
                        import me.bottdev.meshdi.moduleit.api.annotations.Module;
                        import me.bottdev.meshdi.moduleit.api.annotations.DependsOn;
                        
                        @Module(id = "another", version = "0.0.1", apiVersion = "0.0.1",
                        dependencies = { @DependsOn(id = "example", version = ">=0.0.1") })
                        public class AnotherModule {}
                        """
                )
                .buildTo(repoDir, "another-module-0.0.1.jar");

        List<ModuleCandidate> candidates = repository.candidates();
        assertThat(candidates)
                .hasSize(2)
                .extracting(ModuleCandidate::sourceKey)
                .containsExactlyInAnyOrder(
                        "example-module-0.0.1.jar",
                        "another-module-0.0.1.jar"
                );
    }


}