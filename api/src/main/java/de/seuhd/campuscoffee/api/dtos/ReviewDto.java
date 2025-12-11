package de.seuhd.campuscoffee.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * DTO record for POS metadata.
 */
@Builder(toBuilder = true)
public record ReviewDto (
    @Nullable Long id,
    // TODO: Implement ReviewDto
    @Nullable LocalDateTime createdAt,
    @Nullable LocalDateTime updatedAt,

    @NotNull
    @NonNull Long posId,

    @NotNull
    @NonNull Long authorId,

    @NotBlank(message = "Review cannot be empty.")
    @NonNull String review,

    @Nullable Integer approvalCount, // is updated by the domain module
    @Nullable Boolean approved // is determined by the domain module
) implements Dto<Long> {
    @Override
    public @Nullable Long getId() {
        return id;
    }
}
