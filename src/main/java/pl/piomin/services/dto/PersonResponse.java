package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.piomin.services.model.Gender;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.PersonStatus;

import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record PersonResponse(@Schema(requiredMode = REQUIRED) Long id,
                             @Schema(requiredMode = REQUIRED) String firstName,
                             @Schema(requiredMode = REQUIRED) String lastName,
                             @Schema(requiredMode = REQUIRED) String email,
                             LocalDate birthDate, Gender gender, AddressDto address,
                             @Schema(requiredMode = REQUIRED) PersonStatus status,
                             LocalDate availableFrom, LocalDate availableTo,
                             @Schema(requiredMode = REQUIRED) Long version) {

    public static PersonResponse from(Person p) {
        return new PersonResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getEmail(),
                p.getBirthDate(), p.getGender(), AddressDto.from(p.getAddress()), p.getStatus(),
                p.getAvailableFrom(), p.getAvailableTo(), p.getVersion());
    }
}
