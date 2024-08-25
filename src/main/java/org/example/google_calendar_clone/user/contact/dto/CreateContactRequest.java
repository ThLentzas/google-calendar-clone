package org.example.google_calendar_clone.user.contact.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateContactRequest(@NotNull(message = "You must provide the id of the receiver")
                                   @Positive Long receiverId) {
}
