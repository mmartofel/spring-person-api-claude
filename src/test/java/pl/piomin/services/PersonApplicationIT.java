package pl.piomin.services;

import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.piomin.services.config.SecurityConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PersonApplicationIT {

    private static final String URL = "/api/persons";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    MockMvc mvc;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    private static JwtRequestPostProcessor editor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.EDITOR));
    }

    private String create(String firstName, String lastName, String email) throws Exception {
        String body = mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","email":"%s","birthDate":"1990-05-01","gender":"FEMALE"}"""
                                .formatted(firstName, lastName, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id").toString();
    }

    @Test
    void createReplaceDelete() throws Exception {
        String id = create("Anna", "Nowak", "anna@example.com");

        mvc.perform(get(URL + "/" + id).with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Nowak"))
                .andExpect(jsonPath("$.birthDate").value("1990-05-01"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(put(URL + "/" + id).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Anna","lastName":"Kowalska","email":"anna@example.com",
                                 "birthDate":null,"gender":"FEMALE","version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(URL + "/" + id).with(editor()))
                .andExpect(jsonPath("$.lastName").value("Kowalska"))
                .andExpect(jsonPath("$.birthDate").isEmpty())
                .andExpect(jsonPath("$.gender").value("FEMALE"));
        mvc.perform(put(URL + "/" + id).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Anna","lastName":"Stale","email":"anna@example.com","version":0}"""))
                .andExpect(status().isConflict());
        mvc.perform(delete(URL + "/" + id).with(editor())).andExpect(status().isNoContent());
        mvc.perform(get(URL + "/" + id).with(editor())).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailConflict() throws Exception {
        create("Jan", "Kowalski", "jan@example.com");
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jan","lastName":"Inny","email":"jan@example.com"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("unique"));
    }

    @Test
    void pagingAndSearch() throws Exception {
        create("Zofia", "Searchable", "zofia@example.com");
        create("Zenon", "Searchable", "zenon@example.com");

        mvc.perform(get(URL).param("q", "searchABLE").param("size", "1").param("sort", "firstName").with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Zenon"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get(URL).param("sort", "unknown").with(editor()))
                .andExpect(status().isBadRequest());
    }

    private String createWith(String lastName, String email, String extra) throws Exception {
        String body = mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"%s","email":"%s",%s}""".formatted(lastName, email, extra)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id").toString();
    }

    private ResultActions list(String q, String param, String value) throws Exception {
        return mvc.perform(get(URL).param("q", q).param(param, value).param("sort", "email").with(editor()))
                .andExpect(status().isOk());
    }

    @Test
    void addressStatusAndWindowRoundTrip() throws Exception {
        String id = createWith("Roundtrip", "roundtrip@example.com", """
                "address":{"street":"Main 1","postalCode":"00-001","city":"Warsaw","countryCode":"PL"},
                "status":"UNAVAILABLE","availableFrom":"2026-11-01","availableTo":"2026-11-30\"""");
        mvc.perform(get(URL + "/" + id).with(editor()))
                .andExpect(jsonPath("$.address.city").value("Warsaw"))
                .andExpect(jsonPath("$.address.countryCode").value("PL"))
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.availableFrom").value("2026-11-01"))
                .andExpect(jsonPath("$.availableTo").value("2026-11-30"));
        mvc.perform(put(URL + "/" + id).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"Roundtrip","email":"roundtrip@example.com",
                                 "address":{},"availableTo":"2026-12-31","version":0}"""))
                .andExpect(status().isOk());
        mvc.perform(get(URL + "/" + id).with(editor()))
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableFrom").isEmpty())
                .andExpect(jsonPath("$.availableTo").value("2026-12-31"));
    }

    @Test
    void createDefaultsStatusToAvailable() throws Exception {
        String id = createWith("Defaultstatus", "defaultstatus@example.com", "\"gender\":null");
        mvc.perform(get(URL + "/" + id).with(editor())).andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void filterByStatus() throws Exception {
        createWith("Statusfilter", "statusfilter-a@example.com", "\"status\":\"AVAILABLE\"");
        createWith("Statusfilter", "statusfilter-u@example.com", "\"status\":\"UNAVAILABLE\"");

        list("Statusfilter", "status", "UNAVAILABLE")
                .andExpect(jsonPath("$.content[*].email", contains("statusfilter-u@example.com")));
        list("Statusfilter", "status", "AVAILABLE")
                .andExpect(jsonPath("$.content[*].email", contains("statusfilter-a@example.com")));
    }

    @Test
    void filterByAvailableOn() throws Exception {
        createWith("Windowfilter", "w1-open@example.com", "\"status\":\"AVAILABLE\"");
        createWith("Windowfilter", "w2-from@example.com", "\"availableFrom\":\"2026-11-10\"");
        createWith("Windowfilter", "w3-to@example.com", "\"availableTo\":\"2026-11-10\"");
        createWith("Windowfilter", "w4-closed@example.com", "\"availableFrom\":\"2026-11-05\",\"availableTo\":\"2026-11-15\"");
        createWith("Windowfilter", "w5-unavailable@example.com", "\"status\":\"UNAVAILABLE\"");

        list("Windowfilter", "availableOn", "2026-11-10").andExpect(jsonPath("$.content[*].email",
                contains("w1-open@example.com", "w2-from@example.com", "w3-to@example.com", "w4-closed@example.com")));
        list("Windowfilter", "availableOn", "2026-11-04").andExpect(jsonPath("$.content[*].email",
                contains("w1-open@example.com", "w3-to@example.com")));
        list("Windowfilter", "availableOn", "2026-11-16").andExpect(jsonPath("$.content[*].email",
                contains("w1-open@example.com", "w2-from@example.com")));
        list("Windowfilter", "availableOn", "2026-11-15").andExpect(jsonPath("$.content[*].email",
                contains("w1-open@example.com", "w2-from@example.com", "w4-closed@example.com")));
    }

    @Test
    void filterByCountryCode() throws Exception {
        createWith("Countryfilter", "country-pl@example.com", "\"address\":{\"countryCode\":\"PL\"}");
        createWith("Countryfilter", "country-de@example.com", "\"address\":{\"countryCode\":\"DE\"}");
        createWith("Countryfilter", "country-none@example.com", "\"address\":null");

        list("Countryfilter", "countryCode", "PL")
                .andExpect(jsonPath("$.content[*].email", contains("country-pl@example.com")));
        list("Countryfilter", "countryCode", "FR")
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get(URL).param("countryCode", "pl").with(editor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("countryCode"));
    }

    @Test
    void reversedDateRangeRejected() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"Range","email":"range@example.com",
                                 "availableFrom":"2026-11-02","availableTo":"2026-11-01"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("availableTo"))
                .andExpect(jsonPath("$.errors[0].code").value("date-range"));
    }

    @Test
    void existingRowsDefaultToAvailable() throws Exception {
        jdbc.update("""
                insert into person (first_name, last_name, email, version)
                values ('Old', 'Row', 'oldrow@example.com', 0)""");
        Long id = jdbc.queryForObject("select id from person where email = 'oldrow@example.com'", Long.class);
        mvc.perform(get(URL + "/" + id).with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    void databaseRejectsInvalidNewColumns() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into person (first_name, last_name, email, status, version)
                values ('Ewa', 'Status', 'ewa-status@example.com', 'BUSY', 0)"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_person_status");
        assertThatThrownBy(() -> jdbc.update("""
                insert into person (first_name, last_name, email, country_code, version)
                values ('Ewa', 'Country', 'ewa-country@example.com', 'pl', 0)"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_person_country_code");
        assertThatThrownBy(() -> jdbc.update("""
                insert into person (first_name, last_name, email, available_from, available_to, version)
                values ('Ewa', 'Range', 'ewa-range@example.com', DATE '2026-11-02', DATE '2026-11-01', 0)"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_person_available_range");
    }

    @Test
    void migrationsApplied() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void databaseRejectsUnknownGender() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into person (first_name, last_name, email, gender, version)
                values ('Ewa', 'Check', 'ewa@example.com', 'UNKNOWN', 0)"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_person_gender");
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** Regenerates the committed API contract consumed by the Web UI. */
    @Test
    void exportOpenApiContract() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Path file = Path.of("contract/openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, spec + System.lineSeparator());

        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/persons'].get.operationId").value("listPersons"))
                .andExpect(jsonPath("$.paths['/api/persons/{id}'].get.responses['200'].content['application/json']").exists())
                .andExpect(jsonPath("$.components.schemas.PersonResponse.required",
                        containsInAnyOrder("id", "firstName", "lastName", "email", "status", "version")))
                .andExpect(jsonPath("$..pattern", everyItem(is("^[A-Z]{2}$"))))
                .andExpect(jsonPath("$.components.schemas.PersonCreateRequest.properties.status.default").value("AVAILABLE"))
                .andExpect(jsonPath("$.paths['/api/persons'].get.parameters[*].name",
                        hasItems("q", "status", "availableOn", "countryCode", "page", "size", "sort")))
                .andExpect(jsonPath("$.paths['/api/persons'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/persons'].post.responses['409'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/Problem"))
                .andExpect(jsonPath("$.components.schemas.Problem.required", containsInAnyOrder("status", "title")))
                .andExpect(jsonPath("$.components.schemas.FieldError.properties.code.enum",
                        containsInAnyOrder("required", "email", "past", "unique", "invalid", "date-range")));
    }
}
