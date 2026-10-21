package pl.piomin.services.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import pl.piomin.services.model.Gender;

import java.time.LocalDate;

/** Partial update: null fields keep their stored values. {@code version} must match the stored one. */
public record PersonUpdateRequest(@Pattern(regexp = NOT_BLANK, message = NOT_BLANK_MSG) String firstName,
                                  @Pattern(regexp = NOT_BLANK, message = NOT_BLANK_MSG) String lastName,
                                  @Pattern(regexp = NOT_BLANK, message = NOT_BLANK_MSG) @Email String email,
                                  @Past LocalDate birthDate, Gender gender, @NotNull Long version) {

    static final String NOT_BLANK = "(?s).*\\S.*";
    static final String NOT_BLANK_MSG = "must not be blank";
}
