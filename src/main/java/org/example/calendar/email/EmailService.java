package org.example.calendar.email;

import org.example.calendar.event.slot.day.projection.DayEventSlotReminderProjection;
import org.example.calendar.event.day.dto.DayEventInvitationRequest;
import org.example.calendar.event.slot.time.projection.TimeEventSlotReminderProjection;
import org.example.calendar.event.time.dto.TimeEventInvitationRequest;
import org.example.calendar.exception.ServerErrorException;
import org.example.calendar.utils.EmailUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.util.Set;
import java.util.TreeSet;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final ThymeleafService thymeleafService;
    @Value("${spring.mail.username}")
    private String sender;
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String NOTIFICATION = "Notification";
    private static final String INVITATION = "Invitation";

    @Async
    public void sendInvitationEmail(DayEventInvitationRequest invitationRequest) {
        String frequencyText = EmailUtils.buildFrequencyDescription(invitationRequest);
        String context = this.thymeleafService.setInvitationEmailContext(invitationRequest.getStartDate(), invitationRequest.getEventName(), invitationRequest.getOrganizer(), invitationRequest.getLocation(), invitationRequest.getDescription(), frequencyText);

        for (String guestEmail : invitationRequest.getGuestEmails()) {
            sendEmail(guestEmail, INVITATION, context);
        }
    }

    @Async
    public void sendInvitationEmail(TimeEventInvitationRequest invitationRequest) {
        String frequencyText = EmailUtils.buildFrequencyDescription(invitationRequest);
        String context = this.thymeleafService.setInvitationEmailContext(
                invitationRequest.getStartTime().toLocalDate(),
                invitationRequest.getEventName(),
                invitationRequest.getOrganizer(),
                invitationRequest.getLocation(),
                invitationRequest.getDescription(),
                frequencyText
        );

        for (String guestEmail : invitationRequest.getGuestEmails()) {
            sendEmail(guestEmail, INVITATION, context);
        }
    }

    @Async
    public void sendReminderEmail(DayEventSlotReminderProjection projection) {
        String dateDescription = EmailUtils.buildDateDescription(projection.getStartDate());
        String eventSlotDetails = String.format("http://localhost:8080/api/v1/events/day-event-slots/%s", projection.getId());
        String context = this.thymeleafService.setReminderEmailContext(dateDescription, projection.getTitle(), projection.getOrganizerUsername(), projection.getGuestEmails(), eventSlotDetails);

        sendEmail(projection.getOrganizerEmail(), NOTIFICATION, context);
        for (String guest : projection.getGuestEmails()) {
            sendEmail(guest, NOTIFICATION, context);
        }
    }

    @Async
    public void sendReminderEmail(TimeEventSlotReminderProjection projection) {
        String dateDescription = EmailUtils.buildDateTimeDescription(projection.getStartTime(), projection.getEndTime());
        String eventSlotDetails = String.format("http://localhost:8080/api/v1/events/time-event-slots/%s", projection.getId());
        Set<String> guestEmails = new TreeSet<>(projection.getGuestEmails());
        String context = this.thymeleafService.setReminderEmailContext(dateDescription, projection.getTitle(), projection.getOrganizerUsername(), guestEmails, eventSlotDetails);

        sendEmail(projection.getOrganizerEmail(), NOTIFICATION, context);
        for (String guest : projection.getGuestEmails()) {
            sendEmail(guest, NOTIFICATION, context);
        }
    }

    private void sendEmail(String recipient, String subject, String emailContext) {
        MimeMessage mimeMessage = this.mailSender.createMimeMessage();
        MimeMessageHelper helper;

        try {
            helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(recipient);
            helper.setFrom(sender);
            helper.setSubject(subject);
            helper.setText(emailContext, true);

            this.mailSender.send(mimeMessage);
        } catch (MessagingException me) {
            logger.info(me.getMessage());
            throw new ServerErrorException("Internal Server Error");
        }
    }
}
