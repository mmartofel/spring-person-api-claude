package pl.piomin.services.dto;

import java.time.LocalDate;

/** Optional window in which a person can be booked; an empty end means no limit. */
@DateRange
public interface AvailabilityWindow {

    LocalDate availableFrom();

    LocalDate availableTo();
}
