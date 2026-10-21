package pl.piomin.services.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import pl.piomin.services.model.Gender;

import java.time.LocalDate;

/** Full replacement: optional fields sent as null are cleared. {@code version} must match the stored one. */
public record PersonUpdateRequest(@NotBlank String firstName, @NotBlank String lastName,
                                  @NotBlank @Email String email, @Past LocalDate birthDate, Gender gender,
                                  @NotNull Long version) {
}
