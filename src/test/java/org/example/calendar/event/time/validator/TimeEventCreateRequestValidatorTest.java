package org.example.calendar.event.time.validator;

import org.example.calendar.event.recurrence.MonthlyRecurrenceType;
import org.example.calendar.event.recurrence.RecurrenceDuration;
import org.example.calendar.event.recurrence.RecurrenceFrequency;
import org.example.calendar.event.time.dto.TimeEventRequest;
import org.example.calendar.event.groups.OnCreate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

class TimeEventCreateRequestValidatorTest {
    private Validator validator;

    /*
        Spring validator dependency has the Hibernate validator dependency which is the most common implementation of
        the jakarta validator.
        The validate() method looks for any Constraints defined in the object passed as argument. In our case, it finds
        our custom validator and invokes the isValid() method. The return value of the validate() method is a
        Set<ConstraintViolation<T>> violations. Every time a constraint fails, a ConstraintViolation is created and added
        to the Set. The creation of the new ConstraintViolation object is initiated from the buildConstraintViolationWithTemplate()
        and finalized with the call to addConstraintViolation.

        If we added a name for .addConstraintViolation("frequency"); we could also asser to that

        assertThat(violation.getPropertyPath().toString()).hasToString("frequency");

        Since in the @ValidEventDayRequest we specify a group like @ValidDayEventRequest(groups = OnCreate.class)
        if we just validator.validate(request);  it will fail because it uses the Default.class group and is not supported
        by our annotation.

        It is very important to have 1 violation per test, because the set of constraints will have more than 1
        error message and, we can not be sure that iterator.next() will return the constraint we test

        All the dates must be created dynamically relative to now(). If they are hardcoded eventually they will be in the
        past and the validation for future or present dates will consider the request invalid. We can not also call
        LocalDateTime.now() to generate those values. It will generate values with the default time zone. We need to pass
        the time zone the provided: LocalDateTime.now(ZoneId.of("Asia/Tokyo") and startTimeZoneId(ZoneId.of("Asia/Tokyo")

        Both the DayEventCreateRequestValidator and the TimeEventCreateRequestValidator make a call to the overloaded
        method EventUtils.hasValidEventRequestProperties(). For day events, the method calls hasValidDateProperties() and
        then calls hasValidFrequencyProperties(). For time events, method calls hasValidDateTimeProperties() and then
        calls hasValidFrequencyProperties(). In this class, we test every case for the hasValidDateTimeProperties().
        Frequency related cases are tested in the DayEventCreateRequestValidator and we don't have to repeat them.
     */
    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldReturnTrueWhenTimeEventRequestIsValid() {
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event title")
                .startTime(LocalDateTime.now(ZoneId.of("Asia/Dubai")).plusDays(1))
                .endTime(LocalDateTime.now(ZoneId.of("Asia/Dubai")).plusDays(1).plusMinutes(30))
                .startTimeZoneId(ZoneId.of("Asia/Dubai"))
                .endTimeZoneId(ZoneId.of("Asia/Dubai"))
                .recurrenceFrequency(RecurrenceFrequency.NEVER)
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenStartTimeIsInThePast() {
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event title")
                .startTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).minusDays(3))
                .endTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).plusDays(1))
                .startTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .endTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .recurrenceFrequency(RecurrenceFrequency.MONTHLY)
                .monthlyRecurrenceType(MonthlyRecurrenceType.SAME_DAY)
                .recurrenceDuration(RecurrenceDuration.UNTIL_DATE)
                .recurrenceEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<TimeEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Start time must be in the future or present");
    }

    @Test
    void shouldReturnFalseWhenEndTimeIsInThePast() {
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event name")
                .startTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).plusDays(1))
                .endTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).minusDays(3))
                .startTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .endTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .recurrenceFrequency(RecurrenceFrequency.MONTHLY)
                .monthlyRecurrenceType(MonthlyRecurrenceType.SAME_DAY)
                .recurrenceDuration(RecurrenceDuration.UNTIL_DATE)
                .recurrenceEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<TimeEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("End time must be in the future or present");
    }

    @Test
    void shouldReturnFalseWhenStartTimeIsAfterEndTimeWithSameTimezones() {
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event title")
                .startTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).plusDays(3))
                .endTime(LocalDateTime.now(ZoneId.of("Asia/Tokyo")).plusDays(1))
                .startTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .endTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .recurrenceFrequency(RecurrenceFrequency.MONTHLY)
                .monthlyRecurrenceType(MonthlyRecurrenceType.SAME_DAY)
                .recurrenceDuration(RecurrenceDuration.UNTIL_DATE)
                .recurrenceEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<TimeEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Start time must be before end time");
    }

    @Test
    void shouldReturnFalseWhenStartTimeIsAfterEndTimeWithDifferentTimezones() {
        LocalDateTime startTime = LocalDateTime.now(ZoneId.of("America/New_York")).plusDays(2);
        LocalDateTime endTime = LocalDateTime.now(ZoneId.of("Asia/Tokyo")).plusDays(1).plusHours(3);
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event title")
                .startTime(startTime)
                .endTime(endTime)
                .startTimeZoneId(ZoneId.of("America/New_York"))
                .endTimeZoneId(ZoneId.of("Asia/Tokyo"))
                .recurrenceFrequency(RecurrenceFrequency.MONTHLY)
                .monthlyRecurrenceType(MonthlyRecurrenceType.SAME_DAY)
                .recurrenceDuration(RecurrenceDuration.UNTIL_DATE)
                .recurrenceEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<TimeEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Start time must be before end time");
    }

    // Time events can't be more than 24 hours long
    @Test
    void shouldReturnFalseWhenTimeEventSpansMoreThan24HoursAcrossTimeZones() {
        TimeEventRequest request = TimeEventRequest.builder()
                .title("Event title")
                .startTime(LocalDateTime.now(ZoneId.of("Asia/Dubai")).plusDays(1))
                .endTime(LocalDateTime.now(ZoneId.of("Asia/Dubai")).plusDays(2))
                .startTimeZoneId(ZoneId.of("Asia/Dubai"))
                .endTimeZoneId(ZoneId.of("Asia/Dubai"))
                .recurrenceFrequency(RecurrenceFrequency.MONTHLY)
                .monthlyRecurrenceType(MonthlyRecurrenceType.SAME_DAY)
                .recurrenceDuration(RecurrenceDuration.UNTIL_DATE)
                .recurrenceEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<TimeEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<TimeEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Time events can not span for more than 24 hours. Consider creating a Day event instead");
    }
}