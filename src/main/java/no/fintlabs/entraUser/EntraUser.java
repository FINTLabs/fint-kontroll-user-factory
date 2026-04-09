package no.fintlabs.entraUser;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public record EntraUser(
        String id,
        String mail,
        String userPrincipalName,
        String employeeId,
        String studentId,
        Boolean accountEnabled,
        Boolean isDeleted
) {
    public static EntraUser fromRecord(ConsumerRecord<String, EntraUserPayload> record) {
        EntraUserPayload v = record.value();
        return new EntraUser(
                record.key(),
                v.mail(),
                v.userPrincipalName(),
                v.employeeId(),
                v.studentId(),
                v.accountEnabled(),
                false
        );
    }
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
