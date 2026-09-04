package no.fintlabs.user;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class FintStatus {
    /**
     * Source-data validity from FINT. The catalog maps this together with Entra
     * status and validity dates to the final user status.
     */
    public static final String VALID = "VALID";
    public static final String INVALID = "INVALID";
}
