package me.bottdev.meshdi.core.bindings;

import me.bottdev.kern.commons.key.SimpleTypedKey;
import me.bottdev.kern.commons.key.TypedKey;
import me.bottdev.meshdi.api.BeanLifecycleEventHandler;
import me.bottdev.meshdi.api.BeanLifecycleEventType;
import me.bottdev.meshdi.api.BeanResolver;
import me.bottdev.meshdi.api.InitializationStrategy;
import me.bottdev.meshdi.api.ScopeType;
import me.bottdev.meshdi.api.exceptions.BeanLifecycleEventHandleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConstructorBindingTest {

    static class DummyBean {
        public DummyBean() {}
    }

    static class AnotherDummyBean extends DummyBean {
        public AnotherDummyBean() {}
    }

    @Mock
    private BeanResolver mockResolver;

    @Mock
    private BeanLifecycleEventHandler<DummyBean> mockHandler;

    private TypedKey<DummyBean> key;

    @BeforeEach
    void setUp() {
        key = SimpleTypedKey.of(DummyBean.class);
    }

    @Test
    @DisplayName("build: uses default values and key type as implementation")
    void build_defaults() throws Exception {
        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key).build();

        assertThat(binding.getKey()).isEqualTo(key);
        assertThat(binding.getInitializationStrategy()).isEqualTo(InitializationStrategy.LAZY);
        assertThat(binding.getScopeType()).isEqualTo(ScopeType.SINGLETON);
        // By default dependencies are checked against the constructors
        assertThat(binding.getDependencies()).isEmpty();
    }

    @Test
    @DisplayName("build: uses provided values and explicit implementation")
    void build_custom() throws Exception {
        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key)
                .implementation(AnotherDummyBean.class)
                .eager()
                .prototype()
                .build();

        assertThat(binding.getInitializationStrategy()).isEqualTo(InitializationStrategy.EAGER);
        assertThat(binding.getScopeType()).isEqualTo(ScopeType.PROTOTYPE);
        
        DummyBean created = binding.create(mockResolver);
        assertThat(created).isInstanceOf(AnotherDummyBean.class);
    }

    @Test
    @DisplayName("create: uses instantiator to create bean")
    void create_success() throws Exception {
        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key).build();
        
        DummyBean result = binding.create(mockResolver);
        
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(DummyBean.class);
    }

    @Test
    @DisplayName("invokeEventHandler: calls mapped handler")
    void invokeHandler_mapped() throws Exception {
        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        DummyBean bean = new DummyBean();
        binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, bean);

        verify(mockHandler).handle(bean);
    }

    @Test
    @DisplayName("invokeEventHandler: ignores unmapped events")
    void invokeHandler_unmapped() throws Exception {
        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        DummyBean bean = new DummyBean();
        binding.invokeEventHandler(BeanLifecycleEventType.PRE_DESTROY, bean);

        verifyNoInteractions(mockHandler);
    }

    @Test
    @DisplayName("invokeEventHandler: wraps handler exceptions")
    void invokeHandler_throws() throws Exception {
        doThrow(new RuntimeException("Handler error")).when(mockHandler).handle(any());

        ConstructorBinding<DummyBean> binding = ConstructorBinding.builder(key)
                .eventHandler(BeanLifecycleEventType.POST_CONSTRUCT, mockHandler)
                .build();

        DummyBean bean = new DummyBean();
        assertThatThrownBy(() -> binding.invokeEventHandler(BeanLifecycleEventType.POST_CONSTRUCT, bean))
                .isInstanceOf(BeanLifecycleEventHandleException.class);
    }
}
