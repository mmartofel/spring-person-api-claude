package pl.piomin.services.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.Objects;

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
    @Embedded
    private Address address;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonStatus status = PersonStatus.AVAILABLE;
    private LocalDate availableFrom;
    private LocalDate availableTo;
    @Version
    private Long version;

    public void replaceWith(PersonUpdateRequest source) {
        firstName = source.firstName();
        lastName = source.lastName();
        email = source.email();
        birthDate = source.birthDate();
        gender = source.gender();
        address = source.address() == null ? null : source.address().toEntity();
        setStatus(source.status());
        availableFrom = source.availableFrom();
        availableTo = source.availableTo();
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

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public PersonStatus getStatus() {
        return status;
    }

    /** A missing status means {@link PersonStatus#AVAILABLE}. */
    public void setStatus(PersonStatus status) {
        this.status = Objects.requireNonNullElse(status, PersonStatus.AVAILABLE);
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalDate availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalDate getAvailableTo() {
        return availableTo;
    }

    public void setAvailableTo(LocalDate availableTo) {
        this.availableTo = availableTo;
    }

    public Long getVersion() {
        return version;
    }
}
