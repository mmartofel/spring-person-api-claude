package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.data.jpa.domain.Specification;
import pl.piomin.services.model.Task;
import pl.piomin.services.model.TaskPriority;

import java.util.ArrayList;
import java.util.List;

/** Optional list filters, combined with AND. */
public record TaskFilter(
        @Parameter(description = "Case-insensitive search in title and description") String q,
        @Parameter(description = "Done or open tasks") Boolean done,
        @Parameter(description = "Exact priority") TaskPriority priority,
        @Parameter(description = "Id of the assigned person") Long assigneeId) {

    public Specification<Task> toSpecification() {
        List<Specification<Task>> specs = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            specs.add(Filters.search(q, "title", "description"));
        }
        if (done != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("done"), done));
        }
        if (priority != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (assigneeId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
        }
        return Specification.allOf(specs);
    }
}
