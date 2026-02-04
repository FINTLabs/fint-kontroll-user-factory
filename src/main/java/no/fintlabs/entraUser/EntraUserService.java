package no.fintlabs.entraUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.user.User;
import no.fintlabs.user.UserEntityProducerService;
import no.fintlabs.user.UserStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EntraUserService {
    private final FintCache<String, EntraUser> entraUserCache;
    private final UserEntityProducerService userEntityProducerService;
    private final FintCache<String, User> publishUserCache;

    public Optional<EntraUserAttributes> getEntraUserAttributes(String id) {
        return entraUserCache.getOptional(id)
                .map(user -> new EntraUserAttributes(
                        user.mail(),
                        user.userPrincipalName(),
                        user.id(),
                        getEntraStatus(user)
                ));
    }

    public Long getNumberOfEntraUsersInCache() {
        return entraUserCache.getNumberOfDistinctValues();
    }

    private String getEntraStatus(EntraUser user) {
        return Boolean.TRUE.equals(user.isDeleted())
                ? UserStatus.DELETED
                : Boolean.TRUE.equals(user.accountEnabled())
                ? UserStatus.ACTIVE
                : UserStatus.DISABLED;
    }

    public void handleTombstoneForId(String key) {
        log.info("Received tombstone for user: {}", key);
        Optional<EntraUser> optionalEntraUser = entraUserCache.getAllDistinct().stream().filter(u -> u.id().equals(key)).findFirst();
        optionalEntraUser.ifPresentOrElse(this::publishDeletedUser,
                () -> {
                    log.warn("User not found in entra cache: " + key);
                    publishUserCache.getAllDistinct().stream().filter(u -> u.getIdentityProviderUserObjectId().toString().equals(key)).findFirst().ifPresent(
                            user ->
                            {
                                userEntityProducerService.publishTombstoneUser(user.getResourceId());
                                log.info("Published user as deleted: {}", user.getIdentityProviderUserObjectId());
                            }
                    );
                });

    }

    public void publishDeletedUser(EntraUser entraUser) {
        userEntityProducerService.publishTombstoneUser(entraUser.getEmployeeOrStudentId());
        entraUserCache.put(entraUser.getEmployeeOrStudentId(), entraUser.markDeleted());
    }
}
