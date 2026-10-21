package pl.piomin.services;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.piomin.services.config.SecurityConfig;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
                .andExpect(jsonPath("$.errors[0].field").value("email"));
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
                .andExpect(jsonPath("$.paths['/api/persons'].get.operationId").value("listPersons"))
                .andExpect(jsonPath("$.paths['/api/persons/{id}'].get.responses['200'].content['application/json']").exists())
                .andExpect(jsonPath("$.components.schemas.PersonResponse.required",
                        containsInAnyOrder("id", "firstName", "lastName", "email", "version")))
                .andExpect(jsonPath("$..pattern").isEmpty())
                .andReturn().getResponse().getContentAsString();
        Path file = Path.of("contract/openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, spec + System.lineSeparator());
    }
}
