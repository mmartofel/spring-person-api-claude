package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.piomin.services.model.Address;

/** Postal address; all parts are optional. {@code countryCode} is ISO 3166-1 alpha-2. */
@Schema(name = "Address")
public record AddressDto(@Size(max = 120) String street, @Size(max = 16) String postalCode,
                         @Size(max = 80) String city, @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {

    public Address toEntity() {
        return new Address(street, postalCode, city, countryCode);
    }

    public static AddressDto from(Address a) {
        return a == null ? null : new AddressDto(a.street(), a.postalCode(), a.city(), a.countryCode());
    }
}
