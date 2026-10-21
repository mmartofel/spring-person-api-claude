package pl.piomin.services.dto;

import pl.piomin.services.model.Gender;
import pl.piomin.services.model.Person;

import java.time.LocalDate;

public record PersonResponse(Long id, String firstName, String lastName, String email,
                             LocalDate birthDate, Gender gender, Long version) {

    public static PersonResponse from(Person p) {
        return new PersonResponse(p.getId(), p.getFirstName(), p.getLastName(), p.getEmail(),
                p.getBirthDate(), p.getGender(), p.getVersion());
    }
}
