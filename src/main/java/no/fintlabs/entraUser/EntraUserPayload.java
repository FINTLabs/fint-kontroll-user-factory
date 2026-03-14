package no.fintlabs.entraUser;

public record EntraUserPayload(
        String mail,
        String userPrincipalName,
        String employeeId,
        String studentId,
        Boolean accountEnabled
) {}