package pl.piomin.services.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.config.SecurityConfig;
import pl.piomin.services.model.Person;
import pl.piomin.services.repository.PersonRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonController.class)
@Import(SecurityConfig.class)
class PersonControllerTest {

    private static final String VALID = """
            {"firstName":"John","lastName":"Smith","email":"john@example.com","birthDate":"1990-01-01","gender":"MALE"}""";
    private static final String INVALID = """
            {"firstName":"","lastName":"Smith","email":"not-an-email"}""";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    PersonRepository repository;

    private static Person person(Long id) {
        Person p = new Person();
        p.setId(id);
        p.setFirstName("John");
        p.setLastName("Smith");
        p.setEmail("john@example.com");
        return p;
    }

    @Test
    void findAll() throws Exception {
        when(repository.findAll()).thenReturn(List.of(person(1L)));
        mvc.perform(get("/persons").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("John"));
    }

    @Test
    void findById() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(person(1L)));
        mvc.perform(get("/persons/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create() throws Exception {
        when(repository.save(any())).thenReturn(person(1L));
        mvc.perform(post("/persons").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void update() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(person(1L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(put("/persons/1").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1990-01-01"));
    }

    @Test
    void updateKeepsFieldsNotSent() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(person(1L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(put("/persons/1").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void updateInvalid() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(person(1L)));
        mvc.perform(put("/persons/1").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(INVALID))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void deletePerson() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);
        mvc.perform(delete("/persons/1").with(jwt()))
                .andExpect(status().isNoContent());
        verify(repository).deleteById(1L);
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mvc.perform(get("/persons")).andExpect(status().isUnauthorized());
        mvc.perform(post("/persons").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verify(repository, never()).save(any());
    }

    @Test
    void findByIdNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/persons/99").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void updateNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put("/persons/99").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

    @Test
    void deleteNotFound() throws Exception {
        when(repository.existsById(99L)).thenReturn(false);
        mvc.perform(delete("/persons/99").with(jwt())).andExpect(status().isNotFound());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void createInvalid() throws Exception {
        mvc.perform(post("/persons").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(INVALID))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }
}
