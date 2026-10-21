package pl.piomin.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    MockMvc mvc;

    @Test
    void createGetDelete() throws Exception {
        String body = mvc.perform(post("/persons").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Anna","lastName":"Nowak","email":"anna@example.com","gender":"FEMALE"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/persons/" + id).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Nowak"));
        mvc.perform(put("/persons/" + id).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastName":"Kowalska"}"""))
                .andExpect(status().isOk());
        mvc.perform(get("/persons/" + id).with(jwt()))
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.lastName").value("Kowalska"))
                .andExpect(jsonPath("$.gender").value("FEMALE"));
        mvc.perform(delete("/persons/" + id).with(jwt())).andExpect(status().isNoContent());
        mvc.perform(get("/persons/" + id).with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedWithoutToken() throws Exception {
        mvc.perform(get("/persons")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
