package pl.piomin.services.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import pl.piomin.services.dto.PersonCreateRequest;
import pl.piomin.services.dto.PersonFilter;
import pl.piomin.services.dto.PersonResponse;
import pl.piomin.services.dto.PersonUpdateRequest;
import pl.piomin.services.model.Person;
import pl.piomin.services.repository.PersonRepository;

@RestController
@RequestMapping("/api/persons")
@Tag(name = "Persons")
@SecurityRequirement(name = "bearer")
public class PersonController {

    private final PersonRepository repository;

    public PersonController(PersonRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PagedModel<PersonResponse> listPersons(@ParameterObject @Valid PersonFilter filter,
                                                  @ParameterObject Pageable pageable) {
        return new PagedModel<>(repository.findAll(filter.toSpecification(), pageable).map(PersonResponse::from));
    }

    @GetMapping("/{id}")
    public PersonResponse getPerson(@PathVariable Long id) {
        return PersonResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse createPerson(@Valid @RequestBody PersonCreateRequest request) {
        return PersonResponse.from(repository.save(request.toEntity()));
    }

    @PutMapping("/{id}")
    public PersonResponse updatePerson(@PathVariable Long id, @Valid @RequestBody PersonUpdateRequest request) {
        Person person = find(id);
        if (!request.version().equals(person.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Person.class, id);
        }
        person.replaceWith(request);
        return PersonResponse.from(repository.save(person));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePerson(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private Person find(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Person " + id + " not found");
    }
}
