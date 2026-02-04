package no.fintlabs.entraUser;

public record EntraUser(
        String id,
        String mail,
        String userPrincipalName,
        String employeeId,
        String studentId,
        Boolean accountEnabled,
        Boolean isDeleted
) {
    public EntraUser markDeleted() {
        return new EntraUser(
                id,
                mail,
                userPrincipalName,
                employeeId,
                studentId,
                accountEnabled,
                true
        );
    }

    public String getEmployeeOrStudentId() {
        return employeeId != null ? employeeId : studentId;
    }
}
