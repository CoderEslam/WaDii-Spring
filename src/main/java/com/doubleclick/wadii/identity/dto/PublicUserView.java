package com.doubleclick.wadii.identity.dto;

import com.doubleclick.wadii.identity.User;

/** What a counterpart (customer / accepted provider) may see of another user. */
public record PublicUserView(Long id, String firstName, String lastName, String image) {

    public static PublicUserView of(User u) {
        return new PublicUserView(u.getId(), u.getFirstName(), u.getLastName(), u.getImage());
    }
}
