package pl.piomin.services.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import pl.piomin.services.config.SecurityConfig;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.Task;
import pl.piomin.services.model.TaskPriority;
import pl.piomin.services.repository.PersonRepository;
import pl.piomin.services.repository.TaskRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

@WebMvcTest(TaskController.class)
@Import(SecurityConfig.class)
class TaskControllerTest {

    private static final String URL = "/api/tasks";
    private static final String VALID = """
            {"title":"Write report"}""";
    private static final String UPDATE = """
            {"title":"Write report","version":0}""";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    TaskRepository repository;
    @MockitoBean
    PersonRepository personRepository;

    private static JwtRequestPostProcessor editor() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.EDITOR));
    }

    private static JwtRequestPostProcessor viewer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfig.VIEWER));
    }

    private static String create(String extra) {
        return """
                {"title":"Write report",%s}""".formatted(extra);
    }

    private static String update(String extra) {
        return """
                {"title":"Write report","version":0,%s}""".formatted(extra);
    }

    private static Person john() {
        Person p = new Person();
        p.setId(7L);
        p.setFirstName("John");
        p.setLastName("Smith");
        p.setEmail("john@example.com");
        return p;
    }

    private static Task task(Long id) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Write report");
        ReflectionTestUtils.setField(t, "version", 0L);
        return t;
    }

    private void saveReturnsArgument() {
        when(repository.save(any())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(1L);
            ReflectionTestUtils.setField(t, "version", 0L);
            return t;
        });
    }

    private void stored(Task t) {
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listTasks() throws Exception {
        Task t = task(1L);
        t.setAssignee(john());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(t)));
        mvc.perform(get(URL).param("q", "report").param("done", "false").param("priority", "HIGH")
                        .param("assigneeId", "7").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Write report"))
                .andExpect(jsonPath("$.content[0].assignee.id").value(7))
                .andExpect(jsonPath("$.content[0].assignee.label").value("John Smith"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listTasksInvalidDoneFilter() throws Exception {
        assertInvalidFilter("done", "maybe");
    }

    @Test
    void listTasksInvalidPriorityFilter() throws Exception {
        assertInvalidFilter("priority", "URGENT");
    }

    @Test
    void listTasksInvalidAssigneeIdFilter() throws Exception {
        assertInvalidFilter("assigneeId", "abc");
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
    void getTask() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(task(1L)));
        mvc.perform(get(URL + "/1").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assignee").isEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getTaskNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get(URL + "/99").with(viewer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 99 not found"));
    }

    @Test
    void createTaskDefaults() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.assignee").isEmpty());
    }

    @Test
    void createTaskWithAllFields() throws Exception {
        saveReturnsArgument();
        when(personRepository.findById(7L)).thenReturn(Optional.of(john()));
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "description":"Quarterly","priority":"HIGH","estimateHours":12.5,"done":true,
                        "dueDate":"2020-01-31","assigneeId":7""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Quarterly"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.estimateHours").value(12.5))
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.dueDate").value("2020-01-31"))
                .andExpect(jsonPath("$.assignee.id").value(7))
                .andExpect(jsonPath("$.assignee.label").value("John Smith"));
    }

    @Test
    void createTaskUnknownAssignee() throws Exception {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "assigneeId":99""")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("assigneeId"))
                .andExpect(jsonPath("$.errors[0].code").value("not-found"));
        verify(repository, never()).save(any());
    }

    @Test
    void createTaskMissingTitle() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
        verify(repository, never()).save(any());
    }

    @Test
    void createTaskBlankTitle() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":" "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
    }

    @Test
    void createTaskTooLongText() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","description":"%s"}""".formatted("t".repeat(121), "d".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].code", contains("invalid")))
                .andExpect(jsonPath("$.errors[?(@.field == 'description')].code", contains("invalid")));
        verify(repository, never()).save(any());
    }

    @Test
    void createTaskTextAtMaxLength() throws Exception {
        saveReturnsArgument();
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","description":"%s"}""".formatted("t".repeat(120), "d".repeat(2000))))
                .andExpect(status().isCreated());
    }

    @Test
    void createTaskInvalidEstimates() throws Exception {
        for (String estimate : List.of("0.4", "0", "-1", "999.5", "1000", "1.3", "0.75")) {
            mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                            .content(create("\"estimateHours\":" + estimate)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("estimateHours"))
                    .andExpect(jsonPath("$.errors[0].code").value("invalid"));
        }
        verify(repository, never()).save(any());
    }

    @Test
    void createTaskValidEstimates() throws Exception {
        saveReturnsArgument();
        for (String estimate : List.of("0.5", "1", "12.5", "999", "999.0")) {
            mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON)
                            .content(create("\"estimateHours\":" + estimate)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estimateHours").value(new BigDecimal(estimate).doubleValue()));
        }
    }

    @Test
    void createTaskUnknownPriority() throws Exception {
        mvc.perform(post(URL).with(editor()).contentType(MediaType.APPLICATION_JSON).content(create("""
                        "priority":"URGENT\"""")))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @Test
    void updateTaskReplacesRecord() throws Exception {
        Task stored = task(1L);
        stored.setDescription("Old");
        stored.setPriority(TaskPriority.HIGH);
        stored.setEstimateHours(new BigDecimal("2.5"));
        stored.setDone(true);
        stored.setAssignee(john());
        stored(stored);
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"New title","assigneeId":null,"version":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.estimateHours").isEmpty())
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.assignee").isEmpty());
    }

    @Test
    void updateTaskAssigns() throws Exception {
        stored(task(1L));
        when(personRepository.findById(7L)).thenReturn(Optional.of(john()));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "assigneeId":7,"priority":"LOW","done":true""")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.label").value("John Smith"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void updateTaskUnknownAssignee() throws Exception {
        stored(task(1L));
        when(personRepository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "assigneeId":99""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("assigneeId"))
                .andExpect(jsonPath("$.errors[0].code").value("not-found"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateTaskInvalidEstimate() throws Exception {
        stored(task(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(update("""
                        "estimateHours":1.3""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("estimateHours"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateTaskWithoutVersion() throws Exception {
        stored(task(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateTaskStaleVersion() throws Exception {
        stored(task(1L));
        mvc.perform(put(URL + "/1").with(editor()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Write report","version":5}"""))
                .andExpect(status().isConflict());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unexpectedErrorIsProblemDetail() throws Exception {
        when(repository.findById(1L)).thenThrow(new IllegalStateException("internal secret"));
        mvc.perform(get(URL + "/1").with(editor()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected server error"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void updateTaskNotFound() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(put(URL + "/99").with(editor()).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isNotFound());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deleteTask() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);
        mvc.perform(delete(URL + "/1").with(editor())).andExpect(status().isNoContent());
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteTaskNotFound() throws Exception {
        when(repository.existsById(99L)).thenReturn(false);
        mvc.perform(delete(URL + "/99").with(editor())).andExpect(status().isNotFound());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void viewerCannotModify() throws Exception {
        mvc.perform(post(URL).with(viewer()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
        mvc.perform(put(URL + "/1").with(viewer()).contentType(MediaType.APPLICATION_JSON).content(UPDATE))
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
