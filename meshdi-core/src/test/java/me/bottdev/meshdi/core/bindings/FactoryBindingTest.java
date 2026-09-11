package me.bottdev.meshdi.core.bindings;

import me.bottdev.kern.commons.key.SimpleTypedKey;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.kern.dependency.DependOrder;
import me.bottdev.kern.dependency.DependencyLink;
import me.bottdev.kern.dependency.DependencyRequest;
import me.bottdev.meshdi.api.BeanFactory;
import me.bottdev.meshdi.api.BeanLifecycleEventHandler;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import me.bottdev.meshdi.api.exceptions.BindingBuildException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FactoryBindingTest {

    @Mock
    private BeanFactory<String> mockFactory;

    @Mock
    private BeanResolver mockResolver;

    @Mock
    private BeanLifecycleEventHandler<String> mockHandler;

    private TypedKey<String> key;

    @BeforeEach
    void setUp() {
        key = SimpleTypedKey.of(String.class);
    }

    @Test
    @DisplayName("build: uses default values")
    void build_defaults() throws Exception {
        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .build();

        assertThat(binding.getKey()).isEqualTo(key);
        assertThat(binding.getInitializationStrategy()).isEqualTo(InitializationStrategy.LAZY);
        assertThat(binding.getScopeType()).isEqualTo(ScopeType.SINGLETON);
        assertThat(binding.getDependencies()).isEmpty();
    }

    @Test
    @DisplayName("build: uses provided values")
    void build_custom() throws Exception {
        TypedKey<Integer> depKey = SimpleTypedKey.of(Integer.class);

        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .eager()
                .prototype()
                .dependsOn(depKey, DependencyLink.REQUIRED, DependOrder.AFTER)
                .build();

        assertThat(binding.getInitializationStrategy()).isEqualTo(InitializationStrategy.EAGER);
        assertThat(binding.getScopeType()).isEqualTo(ScopeType.PROTOTYPE);
        
        List<DependencyRequest<TypedKey<?>>> dependencies = binding.getDependencies();
        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.getFirst().key()).isEqualTo(depKey);
    }

    @Test
    @DisplayName("build: throws when factory is null")
    void build_nullFactory() {
        assertThatThrownBy(() -> FactoryBinding.builder(key).build())
                .isInstanceOf(BindingBuildException.class)
                .hasMessageContaining("Failed to build a factory binding");
    }

    @Test
    @DisplayName("create: delegates to factory")
    void create_success() throws Exception {
        String expectedBean = "test-bean";
        when(mockFactory.create(mockResolver)).thenReturn(expectedBean);

        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .build();

        String result = binding.create(mockResolver);

        assertThat(result).isEqualTo(expectedBean);
        verify(mockFactory).create(mockResolver);
    }

    @Test
    @DisplayName("invokeEventHandler: calls mapped handler")
    void invokeHandler_mapped() throws Exception {
        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, "bean");

        verify(mockHandler).handle("bean");
    }

    @Test
    @DisplayName("invokeEventHandler: ignores unmapped events")
    void invokeHandler_unmapped() throws Exception {
        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        binding.invokeEventHandler(BeanLifecycleEventType.PRE_DESTROY, "bean");

        verifyNoInteractions(mockHandler);
    }

    @Test
    @DisplayName("invokeEventHandler: wraps handler exceptions")
    void invokeHandler_throws() throws Exception {
        doThrow(new RuntimeException("Handler error")).when(mockHandler).handle(any());

        FactoryBinding<String> binding = FactoryBinding.builder(key)
                .factory(mockFactory)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        assertThatThrownBy(() -> binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, "bean"))
                .isInstanceOf(BeanLifecycleEventHandleException.class);
    }
}
