package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import pl.piomin.services.model.Gender;
import pl.piomin.services.model.PersonStatus;

import java.time.LocalDate;

/**
 * Full replacement: optional fields sent as null are cleared, a missing status becomes AVAILABLE.
 * {@code version} must match the stored one.
 */
public record PersonUpdateRequest(@NotBlank String firstName, @NotBlank String lastName,
                                  @NotBlank @Email String email, @Past LocalDate birthDate, Gender gender,
                                  @Valid AddressDto address,
                                  @Schema(defaultValue = "AVAILABLE") PersonStatus status,
                                  LocalDate availableFrom, LocalDate availableTo,
                                  @NotNull Long version) implements AvailabilityWindow {
}
