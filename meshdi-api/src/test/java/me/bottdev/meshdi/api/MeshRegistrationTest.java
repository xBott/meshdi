package me.bottdev.meshdi.api;

import me.bottdev.meshdi.api.exceptions.mesh.MeshRegistrationBuildException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MeshRegistrationTest {

    @Mock
    private Context mockContext;

    @Test
    @DisplayName("build: creates registration successfully")
    void build_success() throws Exception {
        // Given
        String contextId = "target-context";

        // When
        MeshRegistration registration = MeshRegistration.builder(mockContext)
                .sees(contextId)
                .build();

        // Then
        assertThat(registration).isNotNull();
        assertThat(registration.context()).isEqualTo(mockContext);
        assertThat(registration.sees()).containsExactly(contextId);
    }

    @Test
    @DisplayName("build: throws when context is null")
    void build_throwsOnNullContext() {
        // Given
        Context nullContext = null;

        // When & Then
        assertThatThrownBy(() -> MeshRegistration.builder(nullContext).build())
                .isInstanceOf(MeshRegistrationBuildException.class)
                .hasMessageContaining("Failed to build a DAG Mesh registration")
                .hasCauseInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("build: sees list is immutable (builder)")
    void build_seesImmutableFromBuilder() throws Exception {
        // Given
        MeshRegistration registration = MeshRegistration.builder(mockContext)
                .sees("ctx1")
                .build();

        // When & Then
        assertThatThrownBy(() -> registration.sees().add("ctx2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("constructor: sees list is immutable")
    void constructor_seesImmutable() {
        // Given
        List<String> mutableSees = new ArrayList<>();
        mutableSees.add("ctx1");
        
        MeshRegistration registration = new MeshRegistration(mockContext, mutableSees);
        
        // When
        mutableSees.add("ctx2"); // Modifying original list

        // Then
        assertThat(registration.sees()).containsExactly("ctx1"); // Original list mutation should not affect the registration
        assertThatThrownBy(() -> registration.sees().add("ctx3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
