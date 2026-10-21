package pl.piomin.services.controller;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.config.SecurityConfig;
import pl.piomin.services.model.Person;
import pl.piomin.services.repository.PersonRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final String URL = "/api/persons";
    private static final String VALID = """
            {"firstName":"John","lastName":"Smith","email":"john@example.com","birthDate":"1990-01-01","gender":"MALE"}""";
    private static final String INVALID = """
            {"firstName":"","lastName":"Smith","email":"not-an-email"}""";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    PersonRepository repository;

    private static JwtRequestPostProcessor editor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.EDITOR));
    }

    private static JwtRequestPostProcessor viewer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.VIEWER));
    }

    private static Person person(Long id) {
        Person p = new Person();
        p.setId(id);
        p.setFirstName("John");
        p.setLastName("Smith");
        p.setEmail("john@example.com");
        ReflectionTestUtils.setField(p, "version", 0L);
        return p;
    }

    private void stored(Person p) {
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listPersons() throws Exception {
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person(1L)), PageRequest.of(0, 20), 1));
        mvc.perform(get(URL).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("John"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listPersonsWithQuery() throws Exception {
        when(repository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                eq("smi"), eq("smi"), eq("smi"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person(1L))));
        mvc.perform(get(URL).param("q", "smi").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Smith"));
    }

    @Test
    void listPersonsBadSort() throws Exception {
        when(repository.findAll(any(Pageable.class)))
                .thenThrow(new PropertyReferenceException("foo", TypeInformation.of(Person.class), List.of()));
        mvc.perform(get(URL).param("sort", "foo").with(viewer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPerson() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(person(1L)));
        mvc.perform(get(URL + "/1").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getPersonNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get(URL + "/99").with(viewer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Person 99 not found"));
    }

    @Test
    void createPerson() throws Exception {
        when(repository.save(any())).thenReturn(person(1L));
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createPersonInvalid() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(INVALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonDuplicateEmail() throws Exception {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), Person.UNIQUE_EMAIL)));
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void updatePersonKeepsFieldsNotSent() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    void updatePersonBlankField() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":" ","version":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonWithoutVersion() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonStaleVersion() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe","version":5}"""))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put(URL + "/99").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe","version":0}"""))
                .andExpect(status().isNotFound());
        verify(repository, never()).save(any());
    }

    @Test
    void deletePerson() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);
        mvc.perform(delete(URL + "/1").with(editor())).andExpect(status().isNoContent());
        verify(repository).deleteById(1L);
    }

    @Test
    void deletePersonNotFound() throws Exception {
        when(repository.existsById(99L)).thenReturn(false);
        mvc.perform(delete(URL + "/99").with(editor())).andExpect(status().isNotFound());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void viewerCannotModify() throws Exception {
        mvc.perform(post(URL).with(viewer()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(put(URL + "/1").with(viewer()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/1").with(viewer())).andExpect(status().isForbidden());
        verify(repository, never()).save(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void noRoleIsForbidden() throws Exception {
        mvc.perform(get(URL).with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verify(repository, never()).save(any());
    }
}
