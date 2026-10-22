package pl.piomin.services.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.piomin.services.dto.TaskCreateRequest;
import pl.piomin.services.dto.TaskFilter;
import pl.piomin.services.dto.TaskResponse;
import pl.piomin.services.dto.TaskUpdateRequest;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.Task;
import pl.piomin.services.repository.PersonRepository;
import pl.piomin.services.repository.TaskRepository;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
@SecurityRequirement(name = "bearer")
public class TaskController {

    private final TaskRepository repository;
    private final PersonRepository personRepository;

    public TaskController(TaskRepository repository, PersonRepository personRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
    }

    @GetMapping
    public PagedModel<TaskResponse> listTasks(@ParameterObject @Valid TaskFilter filter,
                                              @ParameterObject Pageable pageable) {
        return new PagedModel<>(repository.findAll(filter.toSpecification(), pageable).map(TaskResponse::from));
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable Long id) {
        return TaskResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TaskResponse createTask(@Valid @RequestBody TaskCreateRequest request) {
        return TaskResponse.from(repository.save(request.toEntity(assignee(request.assigneeId()))));
    }

    /** Transactional, so the assignee is still loaded when mapped; flushed, so version and updatedAt are current. */
    @PutMapping("/{id}")
    @Transactional
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        Task task = find(id);
        Person assignee = assignee(request.assigneeId());
        if (!request.version().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, id);
        }
        task.replaceWith(request, assignee);
        return TaskResponse.from(repository.saveAndFlush(task));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteTask(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private Task find(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private Person assignee(Long assigneeId) {
        return assigneeId == null ? null
                : personRepository.findById(assigneeId).orElseThrow(() -> ApiExceptionHandler.notFound("assigneeId"));
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found");
    }
}
