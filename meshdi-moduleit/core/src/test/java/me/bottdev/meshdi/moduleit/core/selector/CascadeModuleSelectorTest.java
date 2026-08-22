package me.bottdev.meshdi.moduleit.core.selector;

import me.bottdev.meshdi.moduleit.api.ModuleDescriptor;
import me.bottdev.meshdi.moduleit.api.ModuleHandle;
import me.bottdev.meshdi.moduleit.api.ModuleManager;
import me.bottdev.meshdi.moduleit.api.StopModuleSelector;
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

class CascadeModuleSelectorTest {

    static StopModuleSelector strategy;

    @BeforeAll
    static void setUp() {
        strategy = new CascadeModuleSelector();
    }

    @Test
    @DisplayName("selectStop: successfully select modules in correct order")
    void selectStop_success() throws ModuleSelectionException {

        ModuleDescriptor descriptorA = mock(ModuleDescriptor.class);
        when(descriptorA.id()).thenReturn("a");

        ModuleDescriptor descriptorB = mock(ModuleDescriptor.class);
        when(descriptorB.id()).thenReturn("b");

        ModuleDescriptor descriptorC = mock(ModuleDescriptor.class);
        when(descriptorC.id()).thenReturn("c");

        ModuleDescriptor descriptorD = mock(ModuleDescriptor.class);
        when(descriptorD.id()).thenReturn("d");

        ModuleDescriptor descriptorE = mock(ModuleDescriptor.class);
        when(descriptorE.id()).thenReturn("e");

        ModuleHandle handleA = mock(ModuleHandle.class);
        when(handleA.descriptor()).thenReturn(descriptorA);

        ModuleHandle handleB = mock(ModuleHandle.class);
        when(handleB.descriptor()).thenReturn(descriptorB);

        ModuleHandle handleC = mock(ModuleHandle.class);
        when(handleC.descriptor()).thenReturn(descriptorC);

        ModuleHandle handleD = mock(ModuleHandle.class);
        when(handleD.descriptor()).thenReturn(descriptorD);

        ModuleHandle handleE = mock(ModuleHandle.class);
        when(handleE.descriptor()).thenReturn(descriptorE);

        ModuleManager manager = mock(ModuleManager.class);
        when(manager.exists(anyString())).thenReturn(true);
        when(manager.getHandles()).thenReturn(List.of(handleA, handleB, handleC, handleD, handleE));
        when(manager.getDependentHandles("a")).thenReturn(List.of(handleB, handleC));
        when(manager.getDependentHandles("b")).thenReturn(List.of(handleD));
        when(manager.getDependentHandles("c")).thenReturn(List.of(handleD, handleE));
        when(manager.getDependentHandles("d")).thenReturn(List.of(handleE));
        when(manager.getDependentHandles("e")).thenReturn(List.of());

        List<ModuleHandle> groupA = strategy.selectStop("a", manager);
        List<ModuleHandle> groupB = strategy.selectStop("b", manager);
        List<ModuleHandle> groupC = strategy.selectStop("c", manager);
        List<ModuleHandle> groupD = strategy.selectStop("d", manager);
        List<ModuleHandle> groupE = strategy.selectStop("e", manager);

        assertThat(groupA)
                .hasSize(5)
                .containsExactly(handleE, handleD, handleC, handleB, handleA);

        assertThat(groupB)
                .hasSize(3)
                .containsExactly(handleE, handleD, handleB);

        assertThat(groupC)
                .hasSize(3)
                .containsExactly(handleE, handleD, handleC);

        assertThat(groupD)
                .hasSize(2)
                .containsExactly(handleE, handleD);

        assertThat(groupE)
                .hasSize(1)
                .containsExactly(handleE);

    }

    @Test
    @DisplayName("select: throws when module does not exist")
    void selectStop_nonExisting() {

        ModuleManager manager = mock(ModuleManager.class);
        when(manager.exists(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> strategy.selectStop("a", manager));

    }

}