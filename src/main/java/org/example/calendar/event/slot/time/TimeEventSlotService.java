package org.example.calendar.event.slot.time;

import org.example.calendar.event.dto.InviteGuestsRequest;
import org.example.calendar.event.recurrence.MonthlyRecurrenceType;
import org.example.calendar.event.recurrence.RecurrenceDuration;
import org.example.calendar.event.slot.projection.EventSlotWithGuestsProjection;
import org.example.calendar.event.slot.time.dto.TimeEventSlotRequest;
import org.example.calendar.event.slot.time.projection.TimeEventSlotProjection;
import org.example.calendar.event.time.dto.TimeEventRequest;
import org.example.calendar.event.slot.time.projection.TimeEventSlotPublicProjection;
import org.example.calendar.entity.TimeEvent;
import org.example.calendar.entity.TimeEventSlot;
import org.example.calendar.entity.User;
import org.example.calendar.exception.ResourceNotFoundException;
import org.example.calendar.user.UserRepository;
import org.example.calendar.utils.DateUtils;
import org.example.calendar.utils.EventUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TimeEventSlotService {
    private final TimeEventSlotRepository eventSlotRepository;
    private final UserRepository userRepository;
    private static final String EVENT_SLOT_NOT_FOUND_MSG = "Time event slot not found with id: ";

    /*
        For every time event slot, we convert the start/end time to UTC according to their respective timezones. All
        the times stored in the DB will be in UTC. When we return an event slot to the user we convert the UTC time
        back to their local time according to the timezones we stored alongside them. This happens in the converter
     */
    @Transactional
    public void create(TimeEventRequest eventRequest, TimeEvent event) {
        switch (event.getRecurrenceFrequency()) {
            // Non-recurring event, we only create 1 TimeEventSlot
            case NEVER -> createTimeEventSlot(eventRequest, event, event.getStartTime());
            case DAILY -> {
                createUntilDateDailyEventSlots(eventRequest, event);
                createNOccurrencesDailyEventSlots(eventRequest, event);
            }
            case WEEKLY -> {
                createUntilDateWeeklyEventSlots(eventRequest, event);
                createNOccurrencesWeeklyEventSlots(eventRequest, event);
            }
            case MONTHLY -> {
                if (event.getMonthlyRecurrenceType().equals(MonthlyRecurrenceType.SAME_WEEKDAY)) {
                    createUntilDateMonthlySameWeekdayEventSlots(eventRequest, event);
                    createNOccurrencesMonthlySameWeekdayEventSlots(eventRequest, event);
                } else {
                    // For events that are recurring Monthly, over N Months, Annually, or over N years we will use the
                    // same method to take into consideration things like leap years for events at 29 February and
                    // events that are to occur at the last day of the month
                    createUntilDateSameDayEventSlots(eventRequest, event, ChronoUnit.MONTHS);
                    createNOccurrencesSameDayEventSlots(eventRequest, event, ChronoUnit.MONTHS);
                }
            }
            case ANNUALLY -> {
                createUntilDateSameDayEventSlots(eventRequest, event, ChronoUnit.YEARS);
                createNOccurrencesSameDayEventSlots(eventRequest, event, ChronoUnit.YEARS);
            }
        }
    }

    /*
        This method will be tested via an Integration test, because EventUtils.hasSameFrequencyDetails() is already
        tested and the remaining code is just calling delete() and save(). No logic to be tested in the
        TimeEventSlotServiceTest class, create is already fully tested.
     */
    @Transactional
    public void updateEventSlotsForEvent(TimeEventRequest eventRequest, List<TimeEventSlot> eventSlots) {
        TimeEventSlotRequest eventSlotRequest = TimeEventSlotRequest.builder()
                .title(eventRequest.getTitle())
                .location(eventRequest.getLocation())
                .description(eventRequest.getDescription())
                .build();

        for (TimeEventSlot original : eventSlots) {
            TimeEventSlot modified = new TimeEventSlot();
            modified.setId(original.getId());
            EventUtils.setCommonEventSlotProperties(eventSlotRequest, modified);
            modified.setGuestEmails(eventRequest.getGuestEmails());

            this.eventSlotRepository.updateEventSlotForEvent(original, modified);
        }
    }

    /*
        We can not call getReferenceById(), we need the email.

        There are 2 cases where the existsByEventIdAndUserId() could throw ResourceNotFoundException.
            1. Event slot exists but the authenticated user is not the organizer
            2. Event slot does not exist
        We cover both with our existsByEventIdAndUserId(). If the event exists and the user is not organizer it returns
        false. If the event does not exist it also returns false. In theory, the user should exist in our database,
        because we use the id of the current authenticated user. There is also an argument for data integrity problems,
        where the user was deleted and the token was not invalidated.

        Previous approach that was improved:
            TimeEventSlot eventSlot = this.timeEventSlotRepository.findByIdOrThrow(slotId);
            if (!this.timeEventRepository.existsByEventIdAndUserId(eventSlot.getTimeEvent().getId(), userId)) {
                throw new ResourceNotFoundException(EVENT_SLOT_NOT_FOUND_MSG + slotId);
            }
        With the above approach we do the lookup for the eventSlot by id, and then we can check if the user that made
        the request is the organizer of the event. We optimize the query to do the look-up like this:
            WHERE des.id = :slotId AND de.user.id = :userId
        Both cases that are mentioned above are covered by 1 query.
     */
    @Transactional
    public void updateEventSlot(Long userId, UUID slotId, TimeEventSlotRequest eventSlotRequest) {
        TimeEventSlotProjection projection = this.eventSlotRepository.findBySlotAndUserId(slotId, userId).orElseThrow(() -> new ResourceNotFoundException(EVENT_SLOT_NOT_FOUND_MSG + slotId));
        TimeEventSlot original = TimeEventSlot.builder()
                .id(projection.getId())
                .title(projection.getTitle())
                .location(projection.getLocation())
                .description(projection.getDescription())
                .startTime(projection.getStarTime())
                .startTimeZoneId(projection.getStartTimeZoneId())
                .endTime(projection.getEndTime())
                .endTimeZoneId(projection.getEndTimeZoneId())
                .guestEmails(projection.getGuestEmails())
                .build();

        User user = this.userRepository.findAuthUserByIdOrThrow(userId);
        TimeEventSlot modified = new TimeEventSlot(original);
        modified.setStartTime(DateUtils.convertToUTC(eventSlotRequest.getStartTime(), eventSlotRequest.getStartTimeZoneId()));
        modified.setEndTime(DateUtils.convertToUTC(eventSlotRequest.getEndTime(), eventSlotRequest.getEndTimeZoneId()));
        modified.setStartTimeZoneId(eventSlotRequest.getStartTimeZoneId());
        modified.setEndTimeZoneId(eventSlotRequest.getEndTimeZoneId());
        EventUtils.setCommonEventSlotProperties(eventSlotRequest, modified);
        modified.setGuestEmails(EventUtils.processGuestEmails(user, eventSlotRequest.getGuestEmails()));

        this.eventSlotRepository.update(original, modified);
    }

    /*
        We can not call getReferenceById(), we need the email.

        There are 2 cases where the existsByEventIdAndUserId() could throw ResourceNotFoundException.
            1. Event slot exists but the authenticated user is not the organizer
            2. Event slot does not exist
        We cover both with our existsByEventIdAndUserId(). If the event exists and the user is not organizer it returns
        false. If the event does not exist it also returns false. In theory, the user should exist in our database,
        because we use the id of the current authenticated user. There is also an argument for data integrity problems,
        where the user was deleted and the token was not invalidated.

        Previous approach that was improved:
            TimeEventSlot eventSlot = this.timeEventSlotRepository.findByIdOrThrow(slotId);
            if (!this.timeEventRepository.existsByEventIdAndUserId(eventSlot.getTimeEvent().getId(), userId)) {
                throw new ResourceNotFoundException(EVENT_SLOT_NOT_FOUND_MSG + slotId);
            }
        With the above approach we do the lookup for the eventSlot by id, and then we can check if the user that made
        the request is the organizer of the event. We optimize the query to do the look-up like this:
            WHERE des.id = :slotId AND de.user.id = :userId
        Both cases that are mentioned above are covered by 1 query.
     */
    @Transactional
    public void inviteGuests(Long userId, UUID slotId, InviteGuestsRequest inviteGuestsRequest) {
        EventSlotWithGuestsProjection projection = this.eventSlotRepository.findBySlotAndUserIdFetchingGuests(slotId, userId).orElseThrow(() -> new ResourceNotFoundException("Time event slot not found with id: " + slotId));
        User user = this.userRepository.findAuthUserByIdOrThrow(userId);
        Set<String> guestEmails = EventUtils.processGuestEmails(user, inviteGuestsRequest, projection.getGuestEmails());

        this.eventSlotRepository.inviteGuests(projection.getId(), guestEmails);
    }

    public List<TimeEventSlotPublicProjection> findEventSlotsByEventAndUserId(UUID eventId, Long userId) {
        return this.eventSlotRepository.findByEventAndUserId(eventId, userId).stream()
                // Don't use peek
                .map(eventSlot -> {
                    eventSlot.setStartTime(DateUtils.convertFromUTC(eventSlot.getStartTime(), eventSlot.getStartTimeZoneId()));
                    eventSlot.setEndTime(DateUtils.convertFromUTC(eventSlot.getEndTime(), eventSlot.getEndTimeZoneId()));
                    return eventSlot;
                }).toList();
    }

    /*
        Returns an event slot where the user is either the organizer or invited as guest
    */
    public TimeEventSlotPublicProjection findEventSlotById(Long userId, UUID slotId) {
        User user = this.userRepository.findAuthUserByIdOrThrow(userId);
        TimeEventSlotPublicProjection projection = this.eventSlotRepository.findByOrganizerOrGuestEmailAndSlotId(slotId, userId, user.getEmail()).orElseThrow(() -> new ResourceNotFoundException(EVENT_SLOT_NOT_FOUND_MSG + slotId));
        projection.setStartTime(DateUtils.convertFromUTC(projection.getStartTime(), projection.getStartTimeZoneId()));
        projection.setEndTime(DateUtils.convertFromUTC(projection.getEndTime(), projection.getEndTimeZoneId()));

        return projection;
    }

    public List<TimeEventSlotPublicProjection> findEventSlotsByUserInDateRange(User user, LocalDateTime startTime, ZoneId startTimeZoneId, LocalDateTime endTime, ZoneId endTimeZoneId) {
        startTime = startTimeZoneId.equals(ZoneId.of("UTC")) ? startTime : DateUtils.convertToUTC(startTime, startTimeZoneId);
        endTime = endTimeZoneId.equals(ZoneId.of("UTC")) ? endTime : DateUtils.convertToUTC(endTime, endTimeZoneId);

        return this.eventSlotRepository.findByUserInDateRange(user.getId(), user.getEmail(), startTime, endTime).stream()
                // Don't use peek
                .map(eventSlot -> {
                    eventSlot.setStartTime(DateUtils.convertFromUTC(eventSlot.getStartTime(), eventSlot.getStartTimeZoneId()));
                    eventSlot.setEndTime(DateUtils.convertFromUTC(eventSlot.getEndTime(), eventSlot.getEndTimeZoneId()));
                    return eventSlot;
                }).toList();
    }

    // 2 delete queries will be logged, first to delete all the guest emails and then the slot itself
    @Transactional
    public void deleteEventSlotById(UUID slotId, Long userId) {
        int rowsAffected = this.eventSlotRepository.deleteBySlotAndUserId(slotId, userId);
        if (rowsAffected != 1) {
            throw new ResourceNotFoundException(EVENT_SLOT_NOT_FOUND_MSG + slotId);
        }
    }

    // This method is used to delete the current event slots before creating new ones based on the new recurrence
    // properties when we update an event. We need to delete the previous ones and create the new
    public void deleteEventSlotsByEventId(UUID eventId) {
        this.eventSlotRepository.deleteEventSlotsByEventId(eventId);
    }

    /*
        In the plus() method we can pass a unit of time to increase our date in the loop. According to the unit passed
        plusWeeks(), plusDays() etc will be called. It is a way to avoid having a different case for each value

        date = date.plusDays(), date = date.plusWeeks() etc
     */
    private void createUntilDateDailyEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.UNTIL_DATE
                && event.getRecurrenceDuration() != RecurrenceDuration.FOREVER) {
            return;
        }

        LocalDateTime dateTime = event.getStartTime();
        while (!dateTime.toLocalDate().isAfter(event.getRecurrenceEndDate())) {
            createTimeEventSlot(eventRequest, event, dateTime);
            dateTime = dateTime.plusDays(event.getRecurrenceStep());
        }
    }

    private void createNOccurrencesDailyEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.N_OCCURRENCES) {
            return;
        }

        LocalDateTime startTime = event.getStartTime();
        for (int i = 0; i <= event.getNumberOfOccurrences(); i++) {
            createTimeEventSlot(eventRequest, event, startTime);
           /*
                The starDate is updated to the previous value plus the number of the recurrence step which
                can be 1, 2 etc, meaning the event is to occur every 1,2, days until we reach N_OCCURRENCES
            */
            startTime = startTime.plusDays(event.getRecurrenceStep());
        }
    }

    /*
        Weekly events can occur on certain days of week. ["MONDAY", "THURSDAY", "SATURDAY"]. In the request,
        one of those 3 days will correspond to the date provided by the user. We have 2 cases to consider:
            Case 1: The next day to occur is after the day that corresponds to the start date. For example,
            if 2024-09-12 is a Thursday and we want the event to occur on Monday, Monday is before Thursday so,
            we need to adjust the date. We need to find the difference between the day of the start date and the day
            to occur in our case: Thursday - Monday (in ordinal enum values) will give us a positive difference
            which means the day of the start date is after the day to occur, so we need to subtract days from the
            startDate (startDate = date.minus(difference)), Thursday - Monday = 3 in ordinal,
            startDate = date.minus(3) would result in 2024-09-09 which is a Monday, but this is in the past relative
            to our event. In this case, we need to find the next Monday, this is why the check is there
            !startDate.isBefore(event.getStartTime().toLocalDate(). We are interested in future occurrences relative to
            our eventRequest.getStatDate()

            Case 2: Opposite of 1. We need to add the difference but since the number will be negative will add its
            absolute value

            @Test
            void shouldCreateTimeEventSlotsWhenEventIsRecurringEveryNWeeksUntilACertainDate(), TimeEventSlotServiceTest
     */
    private void createUntilDateWeeklyEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.UNTIL_DATE && event.getRecurrenceDuration() != RecurrenceDuration.FOREVER) {
            return;
        }

        LocalDate startDate;
        LocalDateTime startTime;
        LocalDateTime dateTime = event.getStartTime();
        while (!dateTime.toLocalDate().isAfter(event.getRecurrenceEndDate())) {
            for (DayOfWeek dayOfWeek : event.getWeeklyRecurrenceDays()) {
                int differenceInDays = event.getStartTime().getDayOfWeek().getValue() - dayOfWeek.getValue();
                if (differenceInDays > 0) {
                    startDate = LocalDate.from(dateTime.minusDays(differenceInDays));
                } else {
                    // Math.abs() would also, we are using the minus operator, which negates the integer(changes the sing)
                    startDate = LocalDate.from(dateTime).plusDays(-differenceInDays);
                }

                // The start date is within the recurrence end date
                if (!startDate.isBefore(event.getStartTime().toLocalDate()) && !startDate.isAfter(event.getRecurrenceEndDate())) {
                    startTime = startDate.atTime(event.getStartTime().toLocalTime());
                    createTimeEventSlot(eventRequest, event, startTime);
                }
            }
            dateTime = dateTime.plusWeeks(event.getRecurrenceStep());
        }
    }

    /*
        Similar logic with createUntilDateWeeklyEventSlots()

            @Test
            void shouldCreateTimeEventSlotsWhenEventIsRecurringEveryNWeeksForNOccurrences(), TimeEventSlotServiceTest
     */
    private void createNOccurrencesWeeklyEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.N_OCCURRENCES) {
            return;
        }

        int count = 0;
        LocalDate startDate;
        LocalDateTime startTime;
        LocalDateTime dateTime = event.getStartTime();
        while (count < event.getNumberOfOccurrences()) {
            for (DayOfWeek dayOfWeek : event.getWeeklyRecurrenceDays()) {
                int differenceInDays = event.getStartTime().getDayOfWeek().getValue() - dayOfWeek.getValue();
                if (differenceInDays > 0) {
                    startDate = LocalDate.from(dateTime.minusDays(differenceInDays));
                } else {
                    // Math.abs() would also, we are using the minus operator, which negates the integer(changes the sing)
                    startDate = LocalDate.from(dateTime).plusDays(-differenceInDays);
                }
                if (!startDate.isBefore(event.getStartTime().toLocalDate())) {
                    startTime = startDate.atTime(event.getStartTime().toLocalTime());
                    createTimeEventSlot(eventRequest, event, startTime);
                    count++;
                    // During the inner loop the count might be equal or greater than the occurrences
                    if (count == event.getNumberOfOccurrences()) {
                        return;
                    }
                }
            }
            dateTime = dateTime.plusWeeks(event.getRecurrenceStep());
        }
    }

    // Monthly events that occur the same week day until a certain date(2nd Tuesday of the month)
    private void createUntilDateMonthlySameWeekdayEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.UNTIL_DATE && event.getRecurrenceDuration() != RecurrenceDuration.FOREVER) {
            return;
        }

        int occurrences = DateUtils.findDayOfMonthOccurrence(LocalDate.from(event.getStartTime()));
        LocalDate startDate;
        LocalDateTime startTime;
        LocalDateTime dateTime = event.getStartTime();
        while (!dateTime.toLocalDate().isAfter(event.getRecurrenceEndDate())) {
            startDate = DateUtils.findDateOfNthDayOfWeekInMonth(
                    YearMonth.of(dateTime.getYear(), dateTime.getMonth()),
                    event.getStartTime().getDayOfWeek(),
                    occurrences
            );
            // Combines this date with a time to create a LocalDateTime. Returns LocalDateTime formed from this date at
            // the specified time.
            startTime = startDate.atTime(event.getStartTime().toLocalTime());
            createTimeEventSlot(eventRequest, event, startTime);
            dateTime = dateTime.plusMonths(event.getRecurrenceStep());
        }
    }

    // Monthly events that occur the same week day until a number of occurrences(2nd Tuesday of the month)
    private void createNOccurrencesMonthlySameWeekdayEventSlots(TimeEventRequest eventRequest, TimeEvent event) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.N_OCCURRENCES) {
            return;
        }

        int occurrences = DateUtils.findDayOfMonthOccurrence(LocalDate.from(event.getStartTime()));
        LocalDate startDate = LocalDate.from(event.getStartTime());
        LocalDateTime startTime = event.getStartTime();
        for (int i = 0; i <= event.getNumberOfOccurrences(); i++) {
            createTimeEventSlot(eventRequest, event, startTime);
            startDate = LocalDate.from(startDate).plusMonths(event.getRecurrenceStep());
            startDate = DateUtils.findDateOfNthDayOfWeekInMonth(
                    YearMonth.of(startDate.getYear(), startDate.getMonth()),
                    event.getStartTime().getDayOfWeek(),
                    occurrences
            );
            // We want the upcoming events to start the same time as the original one. We find the next date and add the time
            startTime = startDate.atTime(event.getStartTime().toLocalTime());
        }
    }

    private void createUntilDateSameDayEventSlots(TimeEventRequest eventRequest, TimeEvent event, ChronoUnit unit) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.UNTIL_DATE && event.getRecurrenceDuration() != RecurrenceDuration.FOREVER) {
            return;
        }

        int dayOfMonth = event.getStartTime().getDayOfMonth();
        LocalDateTime dateTime = event.getStartTime();
        while (!dateTime.toLocalDate().isAfter(event.getRecurrenceEndDate())) {
            LocalDate adjustedDate = DateUtils.adjustDateForMonth(dayOfMonth, dateTime.toLocalDate());
            LocalDateTime adjustedDateTime = adjustedDate.atTime(dateTime.toLocalTime());
            createTimeEventSlot(eventRequest, event, adjustedDateTime);

            dateTime = dateTime.plus(event.getRecurrenceStep(), unit);
        }
    }

    private void createNOccurrencesSameDayEventSlots(TimeEventRequest eventRequest, TimeEvent event, ChronoUnit unit) {
        if (event.getRecurrenceDuration() != RecurrenceDuration.N_OCCURRENCES) {
            return;
        }

        int dayOfMonth = event.getStartTime().getDayOfMonth();
        LocalDateTime startTime = event.getStartTime();
        for (int i = 0; i <= event.getNumberOfOccurrences(); i++) {
            LocalDate adjustedDate = DateUtils.adjustDateForMonth(dayOfMonth, startTime.toLocalDate());
            LocalDateTime adjustedStartTime = adjustedDate.atTime(startTime.toLocalTime());
            createTimeEventSlot(eventRequest, event, adjustedStartTime);

            startTime = startTime.plus(event.getRecurrenceStep(), unit);
        }
    }

    /*
        The user provides the start time and end time to their preferred timezone. We are storing the start time and
        end time to UTC. When we will display the event to the user, we can convert back to the user's timezone that we
        stored along the start and end time. This way we have consistency in the times stored in our db and we can adjust
        accordingly. The end time in UTC is the start time in UTC plus the event duration calculated aware of different
        timezones.

        Let's consider these 2 dates:
            2024-09-12 10:00 AM EDT
            2024-09-12 4:00 PM CEST

            Without taking timezones into consideration, the duration of the event would be 6 hours, but this not the
            case. If both are converted to UTC, the difference is 0, both are 14:00 UTC. This is why we need to consider
            timezones for the event duration
     */
    private void createTimeEventSlot(TimeEventRequest eventRequest, TimeEvent event, LocalDateTime startTime) {
        startTime = DateUtils.convertToUTC(startTime, eventRequest.getStartTimeZoneId());
        LocalDateTime endTime = startTime.plusMinutes(DateUtils.timeZoneAwareDifference(event.getStartTime(), event.getStartTimeZoneId(), event.getEndTime(), event.getEndTimeZoneId(), ChronoUnit.MINUTES));

        TimeEventSlot timeEventSlot = new TimeEventSlot();
        timeEventSlot.setStartTime(startTime);
        timeEventSlot.setEndTime(endTime);
        timeEventSlot.setStartTimeZoneId(event.getStartTimeZoneId());
        timeEventSlot.setEndTimeZoneId(event.getEndTimeZoneId());
        timeEventSlot.setTitle(eventRequest.getTitle());
        timeEventSlot.setDescription(eventRequest.getDescription());
        timeEventSlot.setLocation(eventRequest.getLocation());
        timeEventSlot.setGuestEmails(eventRequest.getGuestEmails());
        timeEventSlot.setEventId(event.getId());
        this.eventSlotRepository.create(timeEventSlot);
    }
}