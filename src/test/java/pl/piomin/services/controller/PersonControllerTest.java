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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.config.SecurityConfig;
import pl.piomin.services.model.Address;
import pl.piomin.services.model.Gender;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.PersonStatus;
import pl.piomin.services.repository.PersonRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonController.class)
@Import(SecurityConfig.class)
class PersonControllerTest {

    private static final String URL = "/api/persons";
    private static final String VALID = """
            {"firstName":"John","lastName":"Smith","email":"john@example.com","birthDate":"1990-01-01","gender":"MALE"}""";
    private static final String UPDATE = """
            {"firstName":"John","lastName":"Smith","email":"john@example.com","version":0}""";
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

    private static String create(String extra) {
        return """
                {"firstName":"John","lastName":"Smith","email":"john@example.com",%s}""".formatted(extra);
    }

    private static String update(String extra) {
        return """
                {"firstName":"John","lastName":"Smith","email":"john@example.com","version":0,%s}""".formatted(extra);
    }

    private void saveReturnsArgument() {
        when(repository.save(any())).thenAnswer(inv -> {
            Person p = inv.getArgument(0);
            p.setId(1L);
            ReflectionTestUtils.setField(p, "version", 0L);
            return p;
        });
    }

    private void stored(Person p) {
        when(repository.findById(p.getId())).thenReturn(Optional.of(p));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listPersons() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person(1L)), PageRequest.of(0, 20), 1));
        mvc.perform(get(URL).with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("John"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listPersonsWithQuery() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person(1L))));
        mvc.perform(get(URL).param("q", "smi").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Smith"));
    }

    @Test
    void listPersonsBadSort() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenThrow(new PropertyReferenceException("foo", TypeInformation.of(Person.class), List.of()));
        mvc.perform(get(URL).param("sort", "foo").with(viewer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPersonsWithFilters() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(person(1L))));
        mvc.perform(get(URL).param("status", "AVAILABLE").param("availableOn", "2026-11-01")
                        .param("countryCode", "PL").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listPersonsInvalidStatusFilter() throws Exception {
        assertInvalidFilter("status", "FOO");
    }

    @Test
    void listPersonsInvalidAvailableOnFilter() throws Exception {
        assertInvalidFilter("availableOn", "not-a-date");
    }

    @Test
    void listPersonsInvalidCountryCodeFilter() throws Exception {
        assertInvalidFilter("countryCode", "pl");
    }

    private void assertInvalidFilter(String param, String value) throws Exception {
        mvc.perform(get(URL).param(param, value).with(viewer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(param))
                .andExpect(jsonPath("$.errors[0].code").value("invalid"));
        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
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
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field == 'firstName')].code", contains("required")))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].code", contains("email")))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].message", contains("must be a well-formed email address")));
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonFutureBirthDate() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"John","lastName":"Smith","email":"john@example.com","birthDate":"2999-01-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("past"));
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonDefaultsStatusToAvailable() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void createPersonKeepsStatus() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content(create("""
                                "status":"UNAVAILABLE\"""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    void createPersonUnknownStatus() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content(create("""
                                "status":"BUSY\"""")))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonWithAddressAndWindow() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "address":{"street":"Main 1","postalCode":"00-001","city":"Warsaw","countryCode":"PL"},
                        "availableFrom":"2026-11-01","availableTo":"2026-11-30\"""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.street").value("Main 1"))
                .andExpect(jsonPath("$.address.postalCode").value("00-001"))
                .andExpect(jsonPath("$.address.city").value("Warsaw"))
                .andExpect(jsonPath("$.address.countryCode").value("PL"))
                .andExpect(jsonPath("$.availableFrom").value("2026-11-01"))
                .andExpect(jsonPath("$.availableTo").value("2026-11-30"));
    }

    @Test
    void createPersonAddressAtMaxLength() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "address":{"street":"%s","postalCode":"%s","city":"%s"}"""
                        .formatted("s".repeat(120), "p".repeat(16), "c".repeat(80)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.countryCode").isEmpty());
    }

    @Test
    void createPersonInvalidAddress() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "address":{"street":"%s","postalCode":"%s","city":"%s","countryCode":"pl"}"""
                        .formatted("s".repeat(121), "p".repeat(17), "c".repeat(81)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.street')].code", contains("invalid")))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.postalCode')].code", contains("invalid")))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.city')].code", contains("invalid")))
                .andExpect(jsonPath("$.errors[?(@.field == 'address.countryCode')].code", contains("invalid")));
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonCountryCodeTooLong() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "address":{"countryCode":"POL"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("address.countryCode"));
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonValidDateRanges() throws Exception {
        saveReturnsArgument();
        for (String window : List.of("""
                        "availableFrom":"2026-11-01","availableTo":"2026-11-02\"""", """
                        "availableFrom":"2026-11-01","availableTo":"2026-11-01\"""", """
                        "availableFrom":"2026-11-01\"""", """
                        "availableTo":"2026-11-01\"""")) {
            mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create(window)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void createPersonReversedDateRange() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "availableFrom":"2026-11-02","availableTo":"2026-11-01\"""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("availableTo"))
                .andExpect(jsonPath("$.errors[0].code").value("date-range"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be before availableFrom"));
        verify(repository, never()).save(any());
    }

    @Test
    void createPersonDuplicateEmail() throws Exception {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException(), Person.UNIQUE_EMAIL)));
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("unique"));
    }

    @Test
    void updatePersonReplacesRecord() throws Exception {
        Person stored = person(1L);
        stored.setBirthDate(LocalDate.of(1990, 1, 1));
        stored.setGender(Gender.MALE);
        stored(stored);
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"Jane","lastName":"Doe","email":"jane@example.com","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.birthDate").isEmpty())
                .andExpect(jsonPath("$.gender").isEmpty());
    }

    @Test
    void updatePersonClearsNewFields() throws Exception {
        Person stored = person(1L);
        stored.setAddress(new Address("Main 1", "00-001", "Warsaw", "PL"));
        stored.setStatus(PersonStatus.UNAVAILABLE);
        stored.setAvailableFrom(LocalDate.of(2026, 11, 1));
        stored.setAvailableTo(LocalDate.of(2026, 11, 30));
        stored(stored);
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableFrom").isEmpty())
                .andExpect(jsonPath("$.availableTo").isEmpty());
    }

    @Test
    void updatePersonSetsNewFields() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "address":{"city":"Berlin","countryCode":"DE"},"status":"UNAVAILABLE",
                        "availableFrom":"2026-12-01","availableTo":"2026-12-24\"""")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.city").value("Berlin"))
                .andExpect(jsonPath("$.address.countryCode").value("DE"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.availableFrom").value("2026-12-01"))
                .andExpect(jsonPath("$.availableTo").value("2026-12-24"));
    }

    @Test
    void updatePersonReversedDateRange() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "availableFrom":"2026-12-24","availableTo":"2026-12-01\"""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("availableTo"))
                .andExpect(jsonPath("$.errors[0].code").value("date-range"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonInvalidCountryCode() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "address":{"countryCode":"de"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("address.countryCode"))
                .andExpect(jsonPath("$.errors[0].code").value("invalid"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonMissingRequiredField() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"lastName":"Doe","email":"john@example.com","version":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonBlankField() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":" ","lastName":"Doe","email":"john@example.com","version":0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonWithoutVersion() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonStaleVersion() throws Exception {
        stored(person(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName":"John","lastName":"Smith","email":"john@example.com","version":5}"""))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void updatePersonNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put(URL + "/99").with(editor()).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
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
