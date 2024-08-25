package org.example.google_calendar_clone.user.dto.converter;

import org.example.google_calendar_clone.entity.User;
import org.example.google_calendar_clone.user.dto.UserProfile;
import org.springframework.core.convert.converter.Converter;

public class UserProfileConverter implements Converter<User, UserProfile> {

    @Override
    public UserProfile convert(User user) {
        return new UserProfile(user.getId(), user.getUsername());
    }
}
