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
        List<ElevResource> allElevUsersWithElevforhold = elevService.getAllEleverWithElevforhold();


        List<User> allValidElevUsers = allElevUsersWithElevforhold
                .stream()
                .map(elevResource -> createUser(elevResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedElevUsers = userEntityProducerService.publishChangedUsers(allValidElevUsers);
        log.info("Number of elevressurser read from FINT: {}", allElevUsersWithElevforhold.size());
        log.info("Number of elevforhold in cache {}", elevforholdService.getNumberOfElevforholdInCache());
        log.info("Number of users from Entra ID: {}", entraUserService.getNumberOfEntraUsersInCache());
        log.info("Published {} of {} students users in cache ", publishedElevUsers.size(), allValidElevUsers.size());
        log.debug("Ids of published users (students) : {}",
                publishedElevUsers.stream()
                        .map(User::getResourceId)
                        .map(href -> href.substring(href.lastIndexOf("/") + 1))
                        .toList()
        );

    }

    public Optional<User> createUser(ElevResource elevResource, Date currentTime) {
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(elevResource);
        String resourceId = hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") + 1);

        Optional<PersonResource> personResourceOptional = personUtdanningService
                .getPersonUtdanning(elevResource);
        if (personResourceOptional.isEmpty()) {
            log.warn("Creating user (student) failed, resourceId={}, missing personressurs", resourceId);
            return Optional.empty(); // TODO we should mark this user as invalid and send it to the catalog
        }

        List<ElevforholdResource> allLinkedElevForholds = elevforholdService.getAllForElev(elevResource.getElevforhold());
        Optional<ElevforholdResource> mainElevForholdOptional = elevforholdService.getMainElevforhold(allLinkedElevForholds, currentTime);

        if (mainElevForholdOptional.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing or not valid elevforhold", resourceId);
            return Optional.empty(); // TODO we should mark this user as invalid and send it to the catalog
        }

        Optional<SkoleResource> skoleOptional = mainElevForholdOptional
                .flatMap(elevforholdService::getSkole);
        if (skoleOptional.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing skole", resourceId);
            return Optional.empty(); // TODO we should mark this user as invalid and send it to the catalog
        }

        Optional<OrganisasjonselementResource> skoleOrgUnitOptional = skoleOptional
                .flatMap(elevforholdService::getSkoleOrgUnit);
        if (skoleOrgUnitOptional.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing organisasjonelement for skole", resourceId);
            return Optional.empty();
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
        String fintStatus = UserUtils.getFINTElevStatus(mainElevForholdOptional.get(), currentTime);

        Date validFrom = allLinkedElevForholds.stream().map(ElevforholdResource::getGyldighetsperiode).map(Periode::getStart).min(Comparator.naturalOrder()).orElse(null);
        Date validTo = allLinkedElevForholds.stream().map(ElevforholdResource::getGyldighetsperiode).map(Periode::getSlutt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);


        return Optional.of(
                createUser(
                        personResourceOptional.get(),
                        skoleOrgUnitOptional.get().getOrganisasjonsnavn(),
                        skoleOrgUnitOptional.get().getOrganisasjonsId().getIdentifikatorverdi(),
                        entraUserAttributes.get(),
                        resourceId,
                        fintStatus,
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
                            String fintStatus,
                            Date validFrom,
                            Date validTo
    ) {

        log.info("Creating user (student) with resourceId: {}", resourceId);

        return User.builder()
                .resourceId(resourceId)
                .firstName(personResource.getNavn().getFornavn())
                .lastName(personResource.getNavn().getEtternavn())
                .userType(String.valueOf(UserUtils.UserType.STUDENT))
                .mainOrganisationUnitName(organisasjonsnavn)
                .mainOrganisationUnitId(organisasjonsId)
                .identityProviderUserObjectId(UUID.fromString(entraUserAttributes.identityProviderUserObjectId()))
                .email(entraUserAttributes.email())
                .userName(entraUserAttributes.userName())
                .fintStatus(fintStatus)
                .entraStatus(entraUserAttributes.entraStatus())
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }


}
