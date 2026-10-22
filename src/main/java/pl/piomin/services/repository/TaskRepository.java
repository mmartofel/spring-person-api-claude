package pl.piomin.services.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.piomin.services.model.Task;

import java.util.Optional;

/** Reads fetch the assignee in the same query: responses embed its label and open-in-view is off. */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Override
    @EntityGraph(attributePaths = "assignee")
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "assignee")
    Optional<Task> findById(Long id);

    long countByAssigneeId(Long assigneeId);
}
