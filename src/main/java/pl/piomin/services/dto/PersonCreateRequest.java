package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import pl.piomin.services.model.Gender;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.PersonStatus;

import java.time.LocalDate;

public record PersonCreateRequest(@NotBlank String firstName, @NotBlank String lastName,
                                  @NotBlank @Email String email, @Past LocalDate birthDate, Gender gender,
                                  @Valid AddressDto address,
                                  @Schema(defaultValue = "AVAILABLE") PersonStatus status,
                                  LocalDate availableFrom, LocalDate availableTo) implements AvailabilityWindow {

    public Person toEntity() {
        Person p = new Person();
        p.setFirstName(firstName);
        p.setLastName(lastName);
        p.setEmail(email);
        p.setBirthDate(birthDate);
        p.setGender(gender);
        p.setAddress(address == null ? null : address.toEntity());
        p.setStatus(status);
        p.setAvailableFrom(availableFrom);
        p.setAvailableTo(availableTo);
        return p;
    }
}
