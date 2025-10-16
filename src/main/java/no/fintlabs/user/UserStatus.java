package no.fintlabs.user;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class UserStatus {
    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";
    public static final String INVALID = "INVALID";
}
