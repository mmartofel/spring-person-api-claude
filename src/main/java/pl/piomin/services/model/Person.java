package pl.piomin.services.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import pl.piomin.services.dto.PersonUpdateRequest;

import java.time.LocalDate;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = Person.UNIQUE_EMAIL, columnNames = "email"))
public class Person {

    public static final String UNIQUE_EMAIL = "uk_person_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;
    @Column(nullable = false)
    private String email;
    private LocalDate birthDate;
    @Enumerated(EnumType.STRING)
    private Gender gender;
    @Version
    private Long version;

    public void updateFrom(PersonUpdateRequest source) {
        if (source.firstName() != null) firstName = source.firstName();
        if (source.lastName() != null) lastName = source.lastName();
        if (source.email() != null) email = source.email();
        if (source.birthDate() != null) birthDate = source.birthDate();
        if (source.gender() != null) gender = source.gender();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Long getVersion() {
        return version;
    }
}
