package me.bottdev.meshdi.moduleit.core.strategy;

import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.ModuleSelectionStrategy;
import me.bottdev.meshdi.moduleit.api.exceptions.ModuleSelectionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IfUnusedModuleSelectionStrategyTest {

    static ModuleSelectionStrategy strategy;

    @BeforeAll
    static void setUp() {
        strategy = new IfUnusedModuleSelectionStrategy();
    }

    @Test
    @DisplayName("select: successfully select module which is not a dependency of another module")
    void select_success() throws ModuleSelectionException {

        ModuleDescriptor descriptor = mock(ModuleDescriptor.class);
        when(descriptor.id()).thenReturn("b");

        ModuleHandle handle = mock(ModuleHandle.class);
        when(handle.descriptor()).thenReturn(descriptor);

        ModuleManager manager = mock(ModuleManager.class);
        when(manager.exists(anyString())).thenReturn(true);
        when(manager.getDependentHandles("b")).thenReturn(List.of());
        when(manager.getHandle("b")).thenReturn(handle);

        List<ModuleHandle> group = strategy.select("b", manager);

        assertThat(group)
                .hasSize(1)
                .containsExactly(handle);

    }

    @Test
    @DisplayName("select: throws when module does not exist")
    void select_nonExisting() {

        ModuleManager manager = mock(ModuleManager.class);
        when(manager.exists(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> strategy.select("a", manager));

    }

    @Test
    @DisplayName("select: throws when module is a dependency of another module")
    void select_dependency() {

        ModuleDescriptor descriptorA = mock(ModuleDescriptor.class);
        when(descriptorA.id()).thenReturn("a");

        ModuleDescriptor descriptorB = mock(ModuleDescriptor.class);
        when(descriptorB.id()).thenReturn("b");

        ModuleDescriptor descriptorC = mock(ModuleDescriptor.class);
        when(descriptorC.id()).thenReturn("c");

        ModuleHandle handleA = mock(ModuleHandle.class);
        when(handleA.descriptor()).thenReturn(descriptorA);

        ModuleHandle handleB = mock(ModuleHandle.class);
        when(handleB.descriptor()).thenReturn(descriptorB);

        ModuleHandle handleC = mock(ModuleHandle.class);
        when(handleC.descriptor()).thenReturn(descriptorC);

        ModuleManager manager = mock(ModuleManager.class);
        when(manager.exists(anyString())).thenReturn(true);
        when(manager.getDependentHandles("a")).thenReturn(List.of(handleB, handleC));

        ModuleSelectionException ex = assertThrows(ModuleSelectionException.class, () -> strategy.select("a", manager));

        assertThat(ex)
                .hasMessageContaining("used by other modules")
                .hasMessageContaining("b, c");

    }

}