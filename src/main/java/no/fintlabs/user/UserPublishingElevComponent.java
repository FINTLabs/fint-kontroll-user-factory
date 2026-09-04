package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.novari.fint.model.felles.kompleksedatatyper.Periode;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.felles.PersonResource;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.novari.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.novari.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.entraUser.EntraUserAttributes;
import no.fintlabs.entraUser.EntraUserService;
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.resourceServices.ElevService;
import no.fintlabs.resourceServices.ElevforholdService;
import no.fintlabs.resourceServices.PersonUtdanningService;
import no.fintlabs.resourceServices.ResourceLookupResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class UserPublishingElevComponent {
    private final ElevService elevService;
    private final PersonUtdanningService personUtdanningService;
    private final ElevforholdService elevforholdService;
    private final EntraUserService entraUserService;
    private final UserEntityProducerService userEntityProducerService;

    public UserPublishingElevComponent(
            ElevService elevService,
            PersonUtdanningService personUtdanningService,
            ElevforholdService elevforholdService, EntraUserService entraUserService, UserEntityProducerService userEntityProducerService) {
        this.elevService = elevService;
        this.personUtdanningService = personUtdanningService;
        this.elevforholdService = elevforholdService;
        this.entraUserService = entraUserService;
        this.userEntityProducerService = userEntityProducerService;
    }

    @Scheduled(
            initialDelayString = "${fint.kontroll.user.publishing.initial-delay-elev}",
            fixedDelayString = "${fint.kontroll.user.publishing.fixed-delay}"
    )
    public void publishElevUsers() {
        Date currentTime = Date.from(Instant.now());
        log.info("<< Start scheduled import of students >>");
        List<ElevResource> allElevUsers = elevService.getAllElever();


        List<User> allElevUsersToPublish = allElevUsers
                .stream()
                .map(elevResource -> createUser(elevResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedElevUsers = userEntityProducerService.publishChangedUsers(allElevUsersToPublish);
        log.info("Number of elevressurser read from FINT: {}", allElevUsers.size());
        log.info("Number of elevforhold in cache {}", elevforholdService.getNumberOfElevforholdInCache());
        log.info("Number of users from Entra ID: {}", entraUserService.getNumberOfEntraUsersInCache());
        log.info("Published {} of {} students users in cache ", publishedElevUsers.size(), allElevUsersToPublish.size());
        log.debug("Ids of published users (students) : {}",
                publishedElevUsers.stream()
                        .map(User::getResourceId)
                        .map(href -> href.substring(href.lastIndexOf("/") + 1))
                        .toList()
        );

    }

    public Optional<User> createUser(ElevResource elevResource) {
        return createUser(elevResource, Date.from(Instant.now()));
    }

    public Optional<User> createUser(ElevResource elevResource, Date currentTime) {
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(elevResource);
        String resourceId = hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") + 1);

        ResourceLookupResult<PersonResource> personLookup = personUtdanningService
                .resolvePersonUtdanning(elevResource);
        if (personLookup.isMissingLink()) {
            return createInvalidStudent(resourceId, "missing person link");
        }
        if (personLookup.isCacheMiss()) {
            return skipStudent(resourceId, "missing linked person in cache");
        }

        ResourceLookupResult<List<ElevforholdResource>> elevforholdLookup =
                elevforholdService.resolveAllForElev(elevResource.getElevforhold());
        if (elevforholdLookup.isMissingLink()) {
            return createInvalidStudent(resourceId, "missing elevforhold link");
        }
        if (elevforholdLookup.isCacheMiss()) {
            return skipStudent(resourceId, "missing linked elevforhold in cache");
        }

        List<ElevforholdResource> allLinkedElevForholds = elevforholdLookup.resource();
        if (allLinkedElevForholds.isEmpty()) {
            return createInvalidStudent(resourceId, "missing gyldighetsperiode on linked elevforhold");
        }

        Optional<ElevforholdResource> mainElevForholdOptional =
                elevforholdService.getMainElevforhold(allLinkedElevForholds, currentTime);
        if (mainElevForholdOptional.isEmpty()) {
            return createInvalidStudent(resourceId, "missing gyldighetsperiode on linked elevforhold");
        }

        ResourceLookupResult<SkoleResource> skoleLookup =
                elevforholdService.resolveSkole(mainElevForholdOptional.get());
        if (skoleLookup.isMissingLink()) {
            return createInvalidStudent(resourceId, "missing skole link");
        }
        if (skoleLookup.isCacheMiss()) {
            return skipStudent(resourceId, "missing linked skole in cache");
        }

        ResourceLookupResult<OrganisasjonselementResource> skoleOrgUnitLookup =
                elevforholdService.resolveSkoleOrgUnit(skoleLookup.resource());
        if (skoleOrgUnitLookup.isMissingLink()) {
            return createInvalidStudent(resourceId, "missing organisasjonelement link");
        }
        if (skoleOrgUnitLookup.isCacheMiss()) {
            return skipStudent(resourceId, "missing linked organisasjonelement in cache");
        }

        //Entra attributes
        Optional<EntraUserAttributes> entraUserAttributes = entraUserService.getEntraUserAttributes(resourceId);
        if (entraUserAttributes.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing entra user attributes", resourceId);
            return Optional.empty();
        }
        if(entraUserAttributes.get().entraStatus().equals(UserStatus.DELETED)) {
            log.info("Creating user (student) failed, resourceId={}, user is deleted in Entra", resourceId);
            return Optional.empty();
        }

        Date validFrom = allLinkedElevForholds.stream().map(ElevforholdResource::getGyldighetsperiode).map(Periode::getStart).min(Comparator.naturalOrder()).orElse(null);
        Date validTo = allLinkedElevForholds.stream().map(ElevforholdResource::getGyldighetsperiode).map(Periode::getSlutt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        return Optional.of(
                createUser(
                        personLookup.resource(),
                        skoleOrgUnitLookup.resource().getNavn(),
                        skoleOrgUnitLookup.resource().getOrganisasjonsId().getIdentifikatorverdi(),
                        entraUserAttributes.get(),
                        resourceId,
                        validFrom,
                        validTo
                )

        );
    }


    private User createUser(PersonResource personResource,
                            String organisasjonsnavn,
                            String organisasjonsId,
                            EntraUserAttributes entraUserAttributes,
                            String resourceId,
                            Date validFrom,
                            Date validTo
    ) {

        log.info("Creating user (student) with resourceId: {}", resourceId);

        return User.builder()
                .resourceId(resourceId)
                .firstName(personResource.getNavn().getFornavn())
                .lastName(personResource.getNavn().getEtternavn())
                .userType(UserType.STUDENT.name())
                .mainOrganisationUnitName(organisasjonsnavn)
                .mainOrganisationUnitId(organisasjonsId)
                .identityProviderUserObjectId(UUID.fromString(entraUserAttributes.identityProviderUserObjectId()))
                .email(entraUserAttributes.email())
                .userName(entraUserAttributes.userName())
                .fintStatus(FintStatus.VALID)
                .entraStatus(entraUserAttributes.entraStatus())
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }

    private Optional<User> createInvalidStudent(String resourceId, String reason) {
        log.info("Creating user (student) failed, resourceId={}, reason={}", resourceId, reason);

        Optional<EntraUserAttributes> entraUserAttributes = entraUserService.getEntraUserAttributes(resourceId);
        if (entraUserAttributes
                .map(EntraUserAttributes::entraStatus)
                .filter(UserStatus.DELETED::equals)
                .isPresent()) {
            return skipStudent(resourceId, "user is deleted in Entra");
        }

        // Invalid payloads are intentionally minimal. The catalog preserves any
        // existing user details and updates only status/statusChanged.
        User.UserBuilder userBuilder = User.builder()
                .resourceId(resourceId)
                .userType(UserType.STUDENT.name())
                .fintStatus(FintStatus.INVALID);

        entraUserAttributes
                .map(EntraUserAttributes::identityProviderUserObjectId)
                .flatMap(identityProviderUserObjectId ->
                        parseIdentityProviderUserObjectId(resourceId, identityProviderUserObjectId))
                .ifPresent(userBuilder::identityProviderUserObjectId);

        return Optional.of(userBuilder.build());
    }

    private Optional<User> skipStudent(String resourceId, String reason) {
        log.info("Creating user (student) skipped, resourceId={}, reason={}", resourceId, reason);
        return Optional.empty();
    }

    private Optional<UUID> parseIdentityProviderUserObjectId(
            String resourceId,
            String identityProviderUserObjectId
    ) {
        if (identityProviderUserObjectId == null || identityProviderUserObjectId.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(identityProviderUserObjectId));
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Ignoring invalid identityProviderUserObjectId for invalid student, resourceId={}",
                    resourceId
            );
            return Optional.empty();
        }
    }

}
