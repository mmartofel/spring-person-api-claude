package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.Task;
import pl.piomin.services.model.TaskPriority;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaskCreateRequest(@NotBlank @Size(min = 1, max = 120) String title,
                                @Size(max = 2000) String description,
                                @Schema(defaultValue = "MEDIUM") TaskPriority priority,
                                @DecimalMin("0.5") @DecimalMax("999") @MultipleOf("0.5") @Schema(multipleOf = 0.5)
                                BigDecimal estimateHours,
                                @Schema(defaultValue = "false") Boolean done,
                                LocalDate dueDate,
                                @Schema(description = "Id of the assigned person") Long assigneeId) {

    public Task toEntity(Person assignee) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription(description);
        t.setPriority(priority);
        t.setEstimateHours(estimateHours);
        t.setDone(done);
        t.setDueDate(dueDate);
        t.setAssignee(assignee);
        return t;
    }
}
