package pl.piomin.services.model;

import jakarta.persistence.Embeddable;

/** Postal address; Hibernate loads it as {@code null} when all parts are null. */
@Embeddable
public record Address(String street, String postalCode, String city, String countryCode) {
}
