package org.example.google_calendar_clone.calendar.event.day.dto.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.example.google_calendar_clone.calendar.event.day.dto.DayEventRequest;
import org.example.google_calendar_clone.calendar.event.repetition.MonthlyRepetitionType;
import org.example.google_calendar_clone.calendar.event.repetition.RepetitionDuration;
import org.example.google_calendar_clone.calendar.event.repetition.RepetitionFrequency;
import org.example.google_calendar_clone.calendar.event.OnCreate;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// https://www.baeldung.com/javax-validation-groups How to acquire a validator
// https://stackoverflow.com/questions/29069956/how-to-test-validation-annotations-of-a-class-using-junit
class DayEventRequestValidatorTest {
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
     */
    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldReturnTrueWhenDayEventRequestIsValidAndFrequencyIsNever() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.NEVER)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenFrequencyIsMonthlyAndRepetitionMonthlyTypeIsNull() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.MONTHLY)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Please provide a monthly repetition type for monthly repeating " +
                "events");
    }

    @Test
    void shouldReturnFalseWhenFrequencyIsNotMonthlyAndRepetitionMonthlyTypeIsNotNull() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.DAILY)
                .monthlyRepetitionType(MonthlyRepetitionType.SAME_DAY)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Monthly repetition types are only valid for monthly repeating " +
                "events");
    }

    @Test
    void shouldReturnFalseWhenRepetitionDurationIsNullForRepeatedEvents() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.MONTHLY)
                .monthlyRepetitionType(MonthlyRepetitionType.SAME_DAY)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Please specify an end date or a number of repetitions for" +
                " repeating events");
    }


    @Test
    void shouldReturnTrueWhenDayEventRequestIsValidAndRepetitionDurationIsForever() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.DAILY)
                .repetitionDuration(RepetitionDuration.FOREVER)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenRepetitionDurationIsUntilDateAndRepetitionEndDateIsNull() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.DAILY)
                .repetitionDuration(RepetitionDuration.UNTIL_DATE)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("The repetition end date is required when repetition duration is" +
                " set to until a certain date");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void shouldReturnFalseWhenRepetitionDurationIsNRepetitionsAndRepetitionCountIsNullOrZero(Integer repetitionCount) {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.DAILY)
                .repetitionDuration(RepetitionDuration.N_REPETITIONS)
                .repetitionCount(repetitionCount)
                .build();
        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("The number of repetitions is required when repetition duration " +
                "is set to a certain number of repetitions");
    }

    @Test
    void shouldReturnFalseWhenRepetitionEndDateAndRepetitionCountAreBothNotNullForRepeatedEvents() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .repetitionFrequency(RepetitionFrequency.MONTHLY)
                .monthlyRepetitionType(MonthlyRepetitionType.SAME_DAY)
                .repetitionDuration(RepetitionDuration.UNTIL_DATE)
                .repetitionEndDate(LocalDate.now().plusYears(1))
                .repetitionCount(3)
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Specify either a repetition end date or a number of " +
                "repetitions. Not both");
    }

    @Test
    void shouldReturnFalseWhenStartDateIsAfterEndDate() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now().plusDays(3))
                .endDate(LocalDate.now().plusDays(1))
                .repetitionFrequency(RepetitionFrequency.MONTHLY)
                .monthlyRepetitionType(MonthlyRepetitionType.SAME_DAY)
                .repetitionDuration(RepetitionDuration.UNTIL_DATE)
                .repetitionEndDate(LocalDate.now().plusYears(1))
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Start date must be before end date");
    }

    @Test
    void shouldReturnFalseWhenRepetitionEndDateIsBeforeTheEndDate() {
        DayEventRequest request = DayEventRequest.builder()
                .name("Event name")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .repetitionFrequency(RepetitionFrequency.MONTHLY)
                .monthlyRepetitionType(MonthlyRepetitionType.SAME_DAY)
                .repetitionDuration(RepetitionDuration.UNTIL_DATE)
                .repetitionEndDate(LocalDate.now().plusMonths(2))
                .build();

        Set<ConstraintViolation<DayEventRequest>> violations = validator.validate(request, OnCreate.class);
        ConstraintViolation<DayEventRequest> violation = violations.iterator().next();

        assertThat(violation.getMessage()).isEqualTo("Repetition end date must be after end date");
    }
}