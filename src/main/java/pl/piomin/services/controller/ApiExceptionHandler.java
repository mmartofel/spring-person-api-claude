package pl.piomin.services.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.piomin.services.model.Person;

import java.net.URI;
import java.util.List;

/** All errors are RFC 9457 Problem Details; field-level errors are listed in {@code errors}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String REQUIRED = "required";
    public static final String EMAIL = "email";
    public static final String PAST = "past";
    public static final String UNIQUE = "unique";
    public static final String INVALID = "invalid";

    /** Field-level error; {@code code} is stable for UI translation, {@code message} is an English fallback. */
    @Schema(name = "FieldError")
    public record FieldMessage(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
                               @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                                       allowableValues = {REQUIRED, EMAIL, PAST, UNIQUE, INVALID}) String code,
                               @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }

    /** RFC 9457 Problem Details as returned by this API; used only to document error responses. */
    @Schema(name = "Problem")
    public record Problem(URI type,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
                          @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
                          String detail, URI instance, List<FieldMessage> errors) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ex.getBody().setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> new FieldMessage(e.getField(), code(e.getCode()), e.getDefaultMessage()))
                .toList());
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Maps a Bean Validation constraint name to a stable error code. */
    private static String code(String constraint) {
        return switch (constraint) {
            case "NotBlank", "NotNull" -> REQUIRED;
            case "Email" -> EMAIL;
            case "Past" -> PAST;
            case null, default -> INVALID;
        };
    }

    @ExceptionHandler
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        if (ex.getCause() instanceof ConstraintViolationException cve && Person.UNIQUE_EMAIL.equals(cve.getConstraintName())) {
            problem.setDetail("Email is already in use");
            problem.setProperty("errors", List.of(new FieldMessage("email", UNIQUE, "already in use")));
        }
        return problem;
    }

    @ExceptionHandler
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Record was changed by someone else; reload and retry");
    }

    @ExceptionHandler
    ProblemDetail handleBadSort(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
