package no.fintlabs.entraUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.user.User;
import no.fintlabs.user.UserEntityProducerService;
import no.fintlabs.user.UserStatus;
import no.fintlabs.user.UserType;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
        Optional<EntraUser> optionalEntraUser = entraUserCache.getAllDistinct().stream().filter(u -> u.id().equals(key)).findFirst();
        optionalEntraUser.ifPresentOrElse(this::publishDeletedUser,
                () -> {
                    log.warn("User not found in entra cache: " + key);
                    publishUserCache.getAllDistinct().stream().filter(user -> hasIdentityProviderObjectId(user, key)).findFirst().ifPresent(
                            user ->
                            {
                                userEntityProducerService.publishTombstoneUser(user.getResourceId());
                                entraUserCache.put(
                                        user.getResourceId(),
                                        deletedEntraUserFromPublishedUser(key, user)
                                );
                                log.info("Published user as deleted: {}", user.getIdentityProviderUserObjectId());
                            }
                    );
                });

    }

    private boolean hasIdentityProviderObjectId(User user, String key) {
        return Optional.ofNullable(user.getIdentityProviderUserObjectId())
                .map(UUID::toString)
                .filter(identityProviderUserObjectId -> Objects.equals(identityProviderUserObjectId, key))
                .isPresent();
    }

    private EntraUser deletedEntraUserFromPublishedUser(String key, User user) {
        boolean isStudent = UserType.STUDENT.name().equals(user.getUserType());
        return new EntraUser(
                key,
                user.getEmail(),
                user.getUserName(),
                isStudent ? null : user.getResourceId(),
                isStudent ? user.getResourceId() : null,
                null,
                true
        );
    }

    public void publishDeletedUser(EntraUser entraUser) {
        if(entraUser.getEmployeeOrStudentId() == null) {
            log.warn("Skipping deleted-user publish for {} because both employeeId and studentId are missing", entraUser.id());
            return;
        }
        userEntityProducerService.publishTombstoneUser(entraUser.getEmployeeOrStudentId());
        entraUserCache.put(entraUser.getEmployeeOrStudentId(), entraUser.markDeleted());
    }
}
