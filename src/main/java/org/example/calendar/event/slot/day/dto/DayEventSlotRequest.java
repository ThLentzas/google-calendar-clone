package org.example.calendar.event.slot.day.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.example.calendar.event.slot.dto.AbstractEventSlotRequest;
import org.example.calendar.event.slot.day.validator.ValidDayEventSlotRequest;

import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ValidDayEventSlotRequest
@EqualsAndHashCode(callSuper = true)
public class DayEventSlotRequest extends AbstractEventSlotRequest {
    private LocalDate startDate;
    private LocalDate endDate;
}
