package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.piomin.services.model.TaskPriority;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Full replacement: optional fields sent as null are cleared, a missing priority becomes MEDIUM and a missing
 * done becomes false. {@code version} must match the stored one.
 */
public record TaskUpdateRequest(@NotBlank @Size(max = 120) String title,
                                @Size(max = 2000) String description,
                                @Schema(defaultValue = "MEDIUM") TaskPriority priority,
                                @DecimalMin("0.5") @DecimalMax("999") @MultipleOf("0.5") @Schema(multipleOf = 0.5)
                                BigDecimal estimateHours,
                                @Schema(defaultValue = "false") Boolean done,
                                LocalDate dueDate,
                                @Schema(description = "Id of the assigned person") Long assigneeId,
                                @NotNull Long version) {
}
