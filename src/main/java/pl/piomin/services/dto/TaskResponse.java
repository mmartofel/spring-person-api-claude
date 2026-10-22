package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.piomin.services.model.Task;
import pl.piomin.services.model.TaskPriority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record TaskResponse(@Schema(requiredMode = REQUIRED) Long id,
                           @Schema(requiredMode = REQUIRED) String title,
                           String description,
                           @Schema(requiredMode = REQUIRED) TaskPriority priority,
                           BigDecimal estimateHours,
                           @Schema(requiredMode = REQUIRED) boolean done,
                           LocalDate dueDate,
                           Reference assignee,
                           @Schema(requiredMode = REQUIRED, accessMode = Schema.AccessMode.READ_ONLY) Instant createdAt,
                           @Schema(requiredMode = REQUIRED, accessMode = Schema.AccessMode.READ_ONLY) Instant updatedAt,
                           @Schema(requiredMode = REQUIRED) Long version) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getEstimateHours(),
                t.isDone(), t.getDueDate(), Reference.of(t.getAssignee()), t.getCreatedAt(), t.getUpdatedAt(),
                t.getVersion());
    }
}
