package no.fintlabs.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.novari.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.novari.fint.model.resource.felles.PersonResource;
import no.fintlabs.entraUser.EntraUserAttributes;
import no.fintlabs.entraUser.EntraUserService;
import no.fintlabs.resourceServices.ArbeidsforholdService;
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.resourceServices.PersonService;
import no.fintlabs.resourceServices.PersonalressursService;
import no.fintlabs.resourceServices.ResourceLookupResult;
import no.fintlabs.resourceServices.SkoleressursService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPublishingComponent {

    private final PersonService personService;
    private final PersonalressursService personalressursService;
    private final EntraUserService entraUserService;
    private final ArbeidsforholdService arbeidsforholdService;
    private final UserEntityProducerService userEntityProducerService;
    private final SkoleressursService skoleressursService;

    @Scheduled(
            initialDelayString = "${fint.kontroll.user.publishing.initial-delay}",
            fixedDelayString = "${fint.kontroll.user.publishing.fixed-delay}"
    )
    public void publishUsers() {
        Date currentTime = Date.from(Instant.now());
        log.info("<< Start scheduled import of employees >>");
        List<PersonalressursResource> allEmployees = personalressursService.getAllUsersfromCache();

        List<User> allEmployeeUsersToPublish = allEmployees
                .stream()
                .map(personalressursResource -> createUser(personalressursResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedUsers = userEntityProducerService.publishChangedUsers(allEmployeeUsersToPublish);
        log.info("Number of personalressurs read from FINT: {}", allEmployees.size());
        log.info("Number of users from Entra ID: {}", entraUserService.getNumberOfEntraUsersInCache());
        log.info("Published {} of {} employee users in cache", publishedUsers.size(), allEmployeeUsersToPublish.size());
        log.info("<< End scheduled import of employees >>");
    }

    public Optional<User> createUser(PersonalressursResource personalressursResource) {
        return createUser(personalressursResource, Date.from(Instant.now()));
    }

    public Optional<User> createUser(PersonalressursResource personalressursResource, Date currentTime) {

        String resourceId = personalressursService.getResourceId(personalressursResource);

        ResourceLookupResult<List<ArbeidsforholdResource>> arbeidsforholdLookup =
                arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold());
        if (arbeidsforholdLookup.isMissingLink()) {
            return createInvalidEmployee(resourceId, "missing arbeidsforhold link");
        }
        if (arbeidsforholdLookup.isCacheMiss()) {
            return skipEmployee(resourceId, "missing linked arbeidsforhold in cache");
        }

        ResourceLookupResult<PersonResource> personLookup = personService.resolvePerson(personalressursResource);
        if (personLookup.isMissingLink()) {
            return createInvalidEmployee(resourceId, "missing person link");
        }
        if (personLookup.isCacheMiss()) {
            return skipEmployee(resourceId, "missing linked person in cache");
        }

        //Hovedstilling eller stilling med høyest stillingsprosent hvis hovedstilling ikke er spesifisert
        Optional<ArbeidsforholdResource> hovedArbeidsforholdOptional = arbeidsforholdService
                .getHovedArbeidsforhold(arbeidsforholdLookup.resource(), currentTime, resourceId);

        if (hovedArbeidsforholdOptional.isEmpty()) {
            log.debug("Creating user failed, resourceId={}, missing arbeidsforhold", resourceId);
            return skipEmployee(resourceId, "missing valid arbeidsforhold");
        }

        ResourceLookupResult<OrganisasjonselementResource> hovedArbeidsstedLookup =
                arbeidsforholdService.resolveArbeidssted(hovedArbeidsforholdOptional.get(), currentTime);
        if (hovedArbeidsstedLookup.isMissingLink()) {
            return createInvalidEmployee(resourceId, "missing arbeidssted link");
        }
        if (hovedArbeidsstedLookup.isCacheMiss()) {
            return skipEmployee(resourceId, "missing linked arbeidssted in cache");
        }
        if (hovedArbeidsstedLookup.isNotValid()) {
            return skipEmployee(resourceId, "arbeidssted is not valid in selection window");
        }

        //Additional orgunits
        Set<String> additionalArbeidssteder = new HashSet<>();
        List<ArbeidsforholdResource> validArbeidsforhold =
                arbeidsforholdService.getAllValidArbeidsforholdAsList(arbeidsforholdLookup.resource(), currentTime);

        List<Optional<OrganisasjonselementResource>> additionalOrgUnits =
                arbeidsforholdService.getAllArbeidssteder(validArbeidsforhold, currentTime);

        if (!additionalOrgUnits.isEmpty()) {
            additionalArbeidssteder = additionalOrgUnits
                    .stream()
                    .filter(Optional::isPresent)
                    .map(orgUnit -> orgUnit.get().getOrganisasjonsId().getIdentifikatorverdi())
                    .collect(Collectors.toSet());
        }

        Optional<String> lederPersonalressursLinkOptional = Optional.of(hovedArbeidsstedLookup.resource())
                .flatMap(arbeidssted -> ResourceLinkUtil.getOptionalFirstLink(arbeidssted::getLeder));

        //Azure attributes
        Optional<EntraUserAttributes> entraUserAttributes = entraUserService.getEntraUserAttributes(resourceId);
        if (entraUserAttributes.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing azureUserAttributes", resourceId);
            return Optional.empty();
        }
        if(entraUserAttributes.get().entraStatus().equals(UserStatus.DELETED)) {
            log.info("Creating user failed, resourceId={}, user is deleted in Entra", resourceId);
            return Optional.empty();
        }
        return Optional.of(
                createUser(
                        personalressursResource,
                        personLookup.resource(),
                        lederPersonalressursLinkOptional.orElse(""),
                        hovedArbeidsstedLookup.resource().getNavn(),
                        hovedArbeidsstedLookup.resource().getOrganisasjonsId().getIdentifikatorverdi(),
                        additionalArbeidssteder,
                        entraUserAttributes.get(),
                        resourceId)
        );
    }

    private User createUser(
            PersonalressursResource personalressursResource,
            PersonResource personResource,
            String lederPersonalressursHref,
            String organisasjonsnavn,
            String organisasjonsId,
            Set<String> additionalArbeidsteder,
            EntraUserAttributes entraUserAttributes,
            String resourceId
    ) {

        Date validFrom = personalressursResource.getAnsettelsesperiode().getStart();
        Date validTo = personalressursResource.getAnsettelsesperiode().getSlutt();

        String userType = getEmployeeUserType(resourceId);

        log.info("Creating user with resourceId: {}",resourceId);

        return User
                .builder()
                .resourceId(resourceId)
                .firstName(personResource.getNavn().getFornavn())
                .lastName(personResource.getNavn().getEtternavn())
                .userType(userType)
                .mainOrganisationUnitName(organisasjonsnavn)
                .mainOrganisationUnitId(organisasjonsId)
                .organisationUnitIds(additionalArbeidsteder)
                .managerRef(lederPersonalressursHref)
                .identityProviderUserObjectId(UUID.fromString(entraUserAttributes.identityProviderUserObjectId()))
                .email(entraUserAttributes.email())
                .userName(entraUserAttributes.userName())
                .fintStatus(FintStatus.VALID)
                .entraStatus(entraUserAttributes.entraStatus())
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }

    private Optional<User> createInvalidEmployee(String resourceId, String reason) {
        log.info("Creating user failed, resourceId={}, reason={}", resourceId, reason);

        Optional<EntraUserAttributes> entraUserAttributes = entraUserService.getEntraUserAttributes(resourceId);
        if (entraUserAttributes
                .map(EntraUserAttributes::entraStatus)
                .filter(UserStatus.DELETED::equals)
                .isPresent()) {
            return skipEmployee(resourceId, "user is deleted in Entra");
        }

        // Invalid payloads are intentionally minimal. The catalog preserves any
        // existing user details and updates only status/statusChanged.
        User.UserBuilder userBuilder = User.builder()
                .resourceId(resourceId)
                .userType(getEmployeeUserType(resourceId))
                .fintStatus(FintStatus.INVALID);

        entraUserAttributes
                .map(EntraUserAttributes::identityProviderUserObjectId)
                .flatMap(identityProviderUserObjectId ->
                        parseIdentityProviderUserObjectId(resourceId, identityProviderUserObjectId))
                .ifPresent(userBuilder::identityProviderUserObjectId);

        return Optional.of(userBuilder.build());
    }

    private Optional<User> skipEmployee(String resourceId, String reason) {
        log.info("Creating user skipped, resourceId={}, reason={}", resourceId, reason);
        return Optional.empty();
    }

    private String getEmployeeUserType(String resourceId) {
        return skoleressursService.isEmployeeInSchool(resourceId)
                ? UserType.EMPLOYEEFACULTY.name()
                : UserType.EMPLOYEESTAFF.name();
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
                    "Ignoring invalid identityProviderUserObjectId for invalid employee, resourceId={}",
                    resourceId
            );
            return Optional.empty();
        }
    }

}
