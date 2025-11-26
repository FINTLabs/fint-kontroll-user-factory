package no.fintlabs.user;

import lombok.*;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class User {

    private Long id;
    private String resourceId;
    private String firstName;
    private String lastName;
    private String userType;
    private String userName;
    private UUID identityProviderUserObjectId;
    private String mainOrganisationUnitName;
    private String mainOrganisationUnitId;
    private Set<String> organisationUnitIds;
    private String email;
    private String managerRef;
    private String status;
    private Date statusChanged;
    private Date validFrom;
    private Date validTo;

}