package no.fintlabs.entraUser;


public record EntraUserAttributes(
        String email,
        String userName,
        String identityProviderUserObjectId,
        String entraStatus
) {
}