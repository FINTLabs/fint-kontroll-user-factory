package no.fintlabs.azureUser;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.user.UserStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AzureUserService {
    private final FintCache<String,AzureUser> azureUserCache;

    public AzureUserService(FintCache<String, AzureUser> azureUserCache) {
        this.azureUserCache = azureUserCache;
    }

    public Optional<Map<String,String>> getAzureUserAttributes(String employeeIdORStudentId){
        Map<String,String> azureUserAttributes = new HashMap<>();

        AzureUser azureUser = azureUserCache.getOptional(employeeIdORStudentId).orElse(null);
        if (azureUser != null){
            azureUserAttributes.put("email", azureUser.getMail());
            azureUserAttributes.put("userName", azureUser.getUserPrincipalName());
            azureUserAttributes.put("identityProviderUserObjectId", azureUser.getId());
            azureUserAttributes.put("azureStatus", azureUser.isAccountEnabled()? UserStatus.ACTIVE : UserStatus.DISABLED);
        }
        else {
            log.debug("No match for employeeId or studentId {} in azureusercache",
                    employeeIdORStudentId);
            return Optional.empty();
        }

        return Optional.of(azureUserAttributes);
    }

    public Long getNumberOfAzureUsersInCache(){
        return azureUserCache.getNumberOfDistinctValues();
    }


}
