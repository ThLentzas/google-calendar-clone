package org.example.google_calendar_clone.calendar.event.time.slot.dto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class TimeEventSlotDTOJsonTest {
    @Autowired
    private JacksonTester<List<TimeEventSlotDTO>> listJacksonTester;

    @Test
    void shouldSerializeDayEventSlotList() throws IOException {
        TimeEventSlotDTO timeEventSlotDTO1 = TimeEventSlotDTO.builder()
                .id(UUID.fromString("e431687e-7251-4ac6-b797-c107064af135"))
                .name("Event name")
                .location("Location")
                .description("Description")
                .organizer("ellyn.roberts")
                .guestEmails(Set.of())
                .startTime(LocalDateTime.parse("2024-10-11T10:00:00"))
                .endTime(LocalDateTime.parse("2024-10-15T15:00:00"))
                .startTimeZoneId(ZoneId.of("Europe/London"))
                .endTimeZoneId(ZoneId.of("Europe/London"))
                .timeEventId(UUID.fromString("6b9b32f2-3c2a-4420-9d52-781c09f320ce"))
                .build();

        TimeEventSlotDTO timeEventSlotDTO2 = TimeEventSlotDTO.builder()
                .id(UUID.fromString("9aeed400-ad9d-462c-bfb7-c5c307161a8d"))
                .name("Event name")
                .location("Location")
                .description("Description")
                .organizer("ellyn.roberts")
                .guestEmails(Set.of())
                .startTime(LocalDateTime.parse("2024-10-25T10:00:00"))
                .endTime(LocalDateTime.parse("2024-10-29T14:00:00"))
                .startTimeZoneId(ZoneId.of("Europe/London"))
                .endTimeZoneId(ZoneId.of("Europe/London"))
                .timeEventId(UUID.fromString("6b9b32f2-3c2a-4420-9d52-781c09f320ce"))
                .build();

        List<TimeEventSlotDTO> eventSlots = List.of(timeEventSlotDTO1, timeEventSlotDTO2);

        String json = """
                [
                    {
                        "id": "e431687e-7251-4ac6-b797-c107064af135",
                        "name": "Event name",
                        "location": "Location",
                        "description": "Description",
                        "organizer": "ellyn.roberts",
                        "guestEmails": [],
                        "startTime": "2024-10-11T10:00:00",
                        "endTime": "2024-10-15T15:00:00",
                        "startTimeZoneId": "Europe/London",
                        "endTimeZoneId": "Europe/London",
                        "timeEventId": "6b9b32f2-3c2a-4420-9d52-781c09f320ce"
                    },
                    {
                        "id": "9aeed400-ad9d-462c-bfb7-c5c307161a8d",
                        "name": "Event name",
                        "location": "Location",
                        "description": "Description",
                        "organizer": "ellyn.roberts",
                        "guestEmails": [],
                        "startTime": "2024-10-25T10:00:00",
                        "endTime": "2024-10-29T14:00:00",
                        "startTimeZoneId": "Europe/London",
                        "endTimeZoneId": "Europe/London",
                        "timeEventId": "6b9b32f2-3c2a-4420-9d52-781c09f320ce"
                    }
                ]
                """;

        JsonContent<List<TimeEventSlotDTO>> actual = this.listJacksonTester.write(eventSlots);

        assertThat(actual).isEqualToJson(json);
    }
}
