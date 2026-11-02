package me.bottdev.meshdi.moduleit.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleBatchResultTest {

    @Nested
    class Of {

        @Test
        void shouldReturnUnchanged_whenNothingProcessedAndNothingFailed() {
            // When
            ModuleBatchResult<String> result = ModuleBatchResult.of(List.of(), List.of());

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Unchanged.class);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.processed()).isEmpty();
            assertThat(result.failed()).isEmpty();
        }

        @Test
        void shouldReturnSuccess_whenNothingFailed() {
            // When
            ModuleBatchResult<String> result = ModuleBatchResult.of(List.of("a", "b"), List.of());

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Success.class);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.processed()).containsExactly("a", "b");
            assertThat(result.failed()).isEmpty();
        }

        @Test
        void shouldReturnPartialSuccess_whenSomeProcessedAndSomeFailed() {
            // When
            ModuleBatchResult<String> result = ModuleBatchResult.of(List.of("a"), List.of("b"));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.PartialSuccess.class);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.processed()).containsExactly("a");
            assertThat(result.failed()).containsExactly("b");
        }

        @Test
        void shouldReturnFailed_whenNothingProcessed() {
            // When
            ModuleBatchResult<String> result = ModuleBatchResult.of(List.of(), List.of("a", "b"));

            // Then
            assertThat(result).isInstanceOf(ModuleBatchResult.Failed.class);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.processed()).isEmpty();
            assertThat(result.failed()).containsExactly("a", "b");
        }

    }

    @Nested
    class Immutability {

        @Test
        @DisplayName("Result is not affected by later changes of the source lists")
        void shouldCopyLists_whenSourceListsAreModifiedAfterCreation() {
            // Given
            List<String> processed = new ArrayList<>(List.of("a"));
            List<String> failed = new ArrayList<>(List.of("b"));
            ModuleBatchResult<String> result = new ModuleBatchResult.PartialSuccess<>(processed, failed);

            // When
            processed.add("c");
            failed.clear();

            // Then
            assertThat(result.processed()).containsExactly("a");
            assertThat(result.failed()).containsExactly("b");
        }

        @Test
        void shouldExposeUnmodifiableLists() {
            // Given
            ModuleBatchResult<String> success = new ModuleBatchResult.Success<>(List.of("a"));
            ModuleBatchResult<String> failed = new ModuleBatchResult.Failed<>(List.of("b"));

            // When / Then
            assertThatThrownBy(() -> success.processed().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> failed.failed().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldRejectNullElements() {
            // Given
            List<String> withNull = Arrays.asList("a", null);

            // When / Then
            assertThatThrownBy(() -> new ModuleBatchResult.Success<>(withNull))
                    .isInstanceOf(NullPointerException.class);
        }

    }

    @Nested
    class Variants {

        @Test
        void shouldHaveNoFailures_whenSuccess() {
            // When
            ModuleBatchResult<String> result = new ModuleBatchResult.Success<>(List.of("a"));

            // Then
            assertThat(result.failed()).isEmpty();
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void shouldHaveNothingProcessed_whenFailed() {
            // When
            ModuleBatchResult<String> result = new ModuleBatchResult.Failed<>(List.of("a"));

            // Then
            assertThat(result.processed()).isEmpty();
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void shouldSupportExhaustivePatternMatching() {
            // Given
            ModuleBatchResult<String> result = ModuleBatchResult.of(List.of("a"), List.of("b"));

            // When
            String description = switch (result) {
                case ModuleBatchResult.Unchanged<String> _ -> "unchanged";
                case ModuleBatchResult.Success<String>(var processed) -> "success " + processed;
                case ModuleBatchResult.PartialSuccess<String>(var processed, var failed) ->
                        "partial " + processed + " " + failed;
                case ModuleBatchResult.Failed<String>(var failed) -> "failed " + failed;
            };

            // Then
            assertThat(description).isEqualTo("partial [a] [b]");
        }

    }

}
