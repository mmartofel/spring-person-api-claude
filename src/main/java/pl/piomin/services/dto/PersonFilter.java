package pl.piomin.services.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import pl.piomin.services.model.Person;
import pl.piomin.services.model.PersonStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Optional list filters, combined with AND. */
public record PersonFilter(
        @Parameter(description = "Case-insensitive search in first name, last name and email") String q,
        @Parameter(description = "Exact status") PersonStatus status,
        @Parameter(description = "Status AVAILABLE and the date inside [availableFrom, availableTo]; an empty end means no limit")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn,
        @Parameter(description = "ISO 3166-1 alpha-2 country code of the address")
        @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {

    public Specification<Person> toSpecification() {
        List<Specification<Person>> specs = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            specs.add(Filters.search(q, "firstName", "lastName", "email"));
        }
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (availableOn != null) {
            specs.add((root, query, cb) -> cb.and(cb.equal(root.get("status"), PersonStatus.AVAILABLE),
                    cb.or(cb.isNull(root.get("availableFrom")), cb.lessThanOrEqualTo(root.<LocalDate>get("availableFrom"), availableOn)),
                    cb.or(cb.isNull(root.get("availableTo")), cb.greaterThanOrEqualTo(root.<LocalDate>get("availableTo"), availableOn))));
        }
        if (countryCode != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("address").get("countryCode"), countryCode));
        }
        return Specification.allOf(specs);
    }
}
