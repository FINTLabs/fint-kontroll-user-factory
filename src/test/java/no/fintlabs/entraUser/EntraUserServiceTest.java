package no.fintlabs.entraUser;

import no.fintlabs.cache.FintCache;
import no.fintlabs.user.User;
import no.fintlabs.user.UserEntityProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntraUserServiceTest {

    @Mock
    private FintCache<String, EntraUser> entraUserCache;

    @Mock
    private UserEntityProducerService userEntityProducerService;

    @Mock
    private FintCache<String, User> publishUserCache;

    private EntraUserService entraUserService;

    @BeforeEach
    void setUp() {
        entraUserService = new EntraUserService(entraUserCache, userEntityProducerService, publishUserCache);
    }

    @Test
    void handleTombstoneForIdShouldIgnoreCachedUsersWithoutIdentityProviderObjectId() {
        User minimalInvalidUser = User.builder()
                .resourceId("student-1")
                .build();

        when(entraUserCache.getAllDistinct()).thenReturn(List.of());
        when(publishUserCache.getAllDistinct()).thenReturn(List.of(minimalInvalidUser));

        assertThatCode(() -> entraUserService.handleTombstoneForId(UUID.randomUUID().toString()))
                .doesNotThrowAnyException();

        verify(userEntityProducerService, never()).publishTombstoneUser(anyString());
    }

    @Test
    void handleTombstoneForIdShouldPublishTombstoneForCachedUserWithMatchingIdentityProviderObjectId() {
        UUID identityProviderUserObjectId = UUID.randomUUID();
        User cachedUser = User.builder()
                .resourceId("employee-1")
                .identityProviderUserObjectId(identityProviderUserObjectId)
                .build();

        when(entraUserCache.getAllDistinct()).thenReturn(List.of());
        when(publishUserCache.getAllDistinct()).thenReturn(List.of(cachedUser));

        entraUserService.handleTombstoneForId(identityProviderUserObjectId.toString());

        verify(userEntityProducerService).publishTombstoneUser("employee-1");
        verify(entraUserCache).put(eq("employee-1"), argThat(entraUser ->
                entraUser.id().equals(identityProviderUserObjectId.toString())
                        && entraUser.employeeId().equals("employee-1")
                        && entraUser.studentId() == null
                        && Boolean.TRUE.equals(entraUser.isDeleted())
        ));
    }

    @Test
    void handleTombstoneForIdShouldNotPublishWhenNoCachedUserMatchesIdentityProviderObjectId() {
        User cachedUser = User.builder()
                .resourceId("employee-1")
                .identityProviderUserObjectId(UUID.randomUUID())
                .build();

        when(entraUserCache.getAllDistinct()).thenReturn(List.of());
        when(publishUserCache.getAllDistinct()).thenReturn(List.of(cachedUser));

        entraUserService.handleTombstoneForId(UUID.randomUUID().toString());

        verify(userEntityProducerService, never()).publishTombstoneUser(anyString());
    }
}
