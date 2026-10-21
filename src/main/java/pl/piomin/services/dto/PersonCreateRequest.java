package pl.piomin.services.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import pl.piomin.services.model.Gender;
import pl.piomin.services.model.Person;

import java.time.LocalDate;

public record PersonCreateRequest(@NotBlank String firstName, @NotBlank String lastName,
                                  @NotBlank @Email String email, @Past LocalDate birthDate, Gender gender) {

    public Person toEntity() {
        Person p = new Person();
        p.setFirstName(firstName);
        p.setLastName(lastName);
        p.setEmail(email);
        p.setBirthDate(birthDate);
        p.setGender(gender);
        return p;
    }
}
