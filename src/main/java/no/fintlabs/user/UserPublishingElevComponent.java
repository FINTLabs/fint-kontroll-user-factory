package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Kontaktinformasjon;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.felles.PersonResource;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.azureUser.AzureUserService;
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
    private final AzureUserService azureUserService;
    private final UserEntityProducerService userEntityProducerService;

    public UserPublishingElevComponent(
            ElevService elevService,
            PersonUtdanningService personUtdanningService,
            ElevforholdService elevforholdService, AzureUserService azureUserService, UserEntityProducerService userEntityProducerService) {
        this.elevService = elevService;
        this.personUtdanningService = personUtdanningService;
        this.elevforholdService = elevforholdService;
        this.azureUserService = azureUserService;
        this.userEntityProducerService = userEntityProducerService;
    }

    @Scheduled(
            initialDelayString = "${fint.kontroll.user.publishing.initial-delay-elev}",
            fixedDelayString = "${fint.kontroll.user.publishing.fixed-delay}"
    )
    public void publishElevUsers() {
        Date currentTime = Date.from(Instant.now());
        log.info("<< Start scheduled import of students >>");
        List<ElevResource> allElevUsersWithElevforhold = elevService.getAllEleverWithElevforhold(currentTime);


        List<User> allValidElevUsers = allElevUsersWithElevforhold
                .stream()
                .map(elevResource -> createUser(elevResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedElevUsers = userEntityProducerService.publishChangedUsers(allValidElevUsers);
        log.info("Number of elevressurser read from FINT: {}", allElevUsersWithElevforhold.size());
        log.info("Number of elevforhold in cache {}", elevforholdService.getNumberOFElevforholdInCache());
        log.info("Number of users from Entra ID: {}", azureUserService.getNumberOfAzureUsersInCache());
        log.info("Published {} of {} valid students users in cache ", publishedElevUsers.size(), allValidElevUsers.size());
        log.debug("Ids of published users (students) : {}",
                publishedElevUsers.stream()
                        .map(User::getResourceId)
                        .map(href -> href.substring(href.lastIndexOf("/") + 1))
                        .toList()
        );

    }

    private Optional<User> createUser(ElevResource elevResource, Date currentTime) {
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(elevResource);
        String resourceId = hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") + 1);
        boolean isUserOnKafka = UserUtils.isUserAlreadyOnKafka(resourceId);

        Optional<PersonResource> personResourceOptional = personUtdanningService
                .getPersonUtdanning(elevResource);
        if (personResourceOptional.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing personressurs", resourceId);
            return Optional.empty();
        }


        Optional<ElevforholdResource> elevforholdOptional =
                elevforholdService.getElevforhold(elevResource.getElevforhold(), currentTime);
        if (elevforholdOptional.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing or not valid elevforhold", resourceId);
            return Optional.empty();
        }

        Optional<SkoleResource> skoleOptional = elevforholdOptional
                .flatMap(elevforhold -> elevforholdService.getSkole(elevforhold, currentTime));
        if (skoleOptional.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing skole", resourceId);
            return Optional.empty();
        }

        Optional<OrganisasjonselementResource> skoleOrgUnitOptional = skoleOptional
                .flatMap(skole -> elevforholdService.getSkoleOrgUnit(skole, currentTime));
        if (skoleOrgUnitOptional.isEmpty()) {
            log.info("Creating user (student) failed, resourceId={}, missing organisasjonelement for skole", resourceId);
            return Optional.empty();
        }

        //Azure attributes
        Optional<Map<String, String>> azureUserAttributes = azureUserService.getAzureUserAttributes(resourceId);
        if (azureUserAttributes.isEmpty() && !isUserOnKafka) {
            log.info("Creating user (student) failed, resourceId={}, missing azure user attributes", resourceId);
            return Optional.empty();
        }

        //TODO: move to separate method
        if (azureUserAttributes.isEmpty()) {
            Map<String, String> attributes = new HashMap<>();
            User userOnKafka = UserUtils.getUserFromKafka(resourceId);
            attributes.put("email", userOnKafka.getEmail());
            attributes.put("userName", userOnKafka.getUserName());
            attributes.put("identityProviderUserObjectId", userOnKafka.getIdentityProviderUserObjectId() != null ? userOnKafka.getIdentityProviderUserObjectId().toString() : null);
            attributes.put("azureStatus", userOnKafka.getStatus());
            azureUserAttributes = Optional.of(attributes);
        }

        String fintStatus = UserUtils.getFINTElevStatus(elevforholdOptional.get(), currentTime);

        Date validFrom = elevforholdOptional.get().getGyldighetsperiode().getStart();
        Date validTo = elevforholdOptional.get().getGyldighetsperiode().getSlutt();

        Date statusChanged = fintStatus.equals("ACTIVE") ? validFrom : validTo;


        return Optional.of(
                createUser(
                        elevResource,
                        personResourceOptional.get(),
                        skoleOrgUnitOptional.get().getOrganisasjonsnavn(),
                        skoleOrgUnitOptional.get().getOrganisasjonsId().getIdentifikatorverdi(),
                        azureUserAttributes.get(),
                        resourceId,
                        fintStatus,
                        statusChanged,
                        validFrom,
                        validTo
                )

        );
    }

    private User createUser(
            ElevResource elevResource,
            PersonResource personResource,
            String organisasjonsnavn,
            String organisasjonsId,
            Map<String, String> azureUserAttributes,
            String resourceId,
            String fintStatus,
            Date statusChanged,
            Date validFrom,
            Date validTo
    ) {


//        String mobilePhone = Optional.ofNullable(personResource.getKontaktinformasjon())
//                .map(Kontaktinformasjon::getMobiltelefonnummer)
//                .orElse("");


        String userStatus = azureUserAttributes.getOrDefault("azureStatus", "").equals("ACTIVE")
                && fintStatus.equals("ACTIVE") ? "ACTIVE" : "DISABLED";

        log.info("Creating user (student) with resourceId: {}", resourceId);

        return User.builder()
                .resourceId(resourceId)
                .firstName(personResource.getNavn().getFornavn())
                .lastName(personResource.getNavn().getEtternavn())
                .userType(String.valueOf(UserUtils.UserType.STUDENT))
                .mainOrganisationUnitName(organisasjonsnavn)
                .mainOrganisationUnitId(organisasjonsId)
                .mobilePhone(null)
                .identityProviderUserObjectId(UUID.fromString(azureUserAttributes.getOrDefault("identityProviderUserObjectId", "0-0-0-0-0")))
                .email(azureUserAttributes.getOrDefault("email", ""))
                .userName(azureUserAttributes.getOrDefault("userName", ""))
                .status(userStatus)
                .statusChanged(statusChanged)
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }
}
