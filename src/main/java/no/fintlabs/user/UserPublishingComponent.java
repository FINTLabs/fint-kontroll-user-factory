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

        List<User> allValidEmployeeUsers = allEmployees
                .stream()
                .map(personalressursResource -> createUser(personalressursResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedUsers = userEntityProducerService.publishChangedUsers(allValidEmployeeUsers);
        log.info("Number of personalressurs read from FINT: {}", allEmployees.size());
        log.info("Number of users from Entra ID: {}", entraUserService.getNumberOfEntraUsersInCache());
        log.info("Published {} of {} employee users in cache", publishedUsers.size(), allValidEmployeeUsers.size());
        log.info("<< End scheduled import of employees >>");
    }

    public Optional<User> createUser(PersonalressursResource personalressursResource, Date currentTime) {

        String resourceId = personalressursService.getResourceId(personalressursResource);

        Optional<PersonResource> personResourceOptional = personService.getPerson(personalressursResource);
        if (personResourceOptional.isEmpty()) {
            log.warn("Creating user failed, resourceId={}, missing personResource", resourceId);
            return Optional.empty(); // TODO we should mark this user as invalid and send it to the catalog
        }

        //Hovedstilling eller stilling med høyest stillingsprosent hvis hovedstilling ikke er spesifisert
        Optional<ArbeidsforholdResource> hovedArbeidsforholdOptional =
                arbeidsforholdService.getHovedArbeidsforhold(personalressursResource.getArbeidsforhold(), currentTime, resourceId);

        if (hovedArbeidsforholdOptional.isEmpty()) {
            log.debug("Creating user failed, resourceId={}, missing arbeidsforhold", resourceId);
            return Optional.empty(); // TODO we should mark this user as invalid and send it to the catalog
        }

        Optional<OrganisasjonselementResource> hovedArbeidsstedOptional = hovedArbeidsforholdOptional
                .flatMap(arbeidsforhold -> arbeidsforholdService.getArbeidssted(arbeidsforhold, currentTime));

        //Additional orgunits
        Set<String> additionalArbeidssteder = new HashSet<>();

        List<ArbeidsforholdResource> additionalArbeidsforhold =
                arbeidsforholdService.getAllValidArbeidsforholdAsList(personalressursResource.getArbeidsforhold(),
                        currentTime, resourceId);

        List<Optional<OrganisasjonselementResource>> additionalOrgUnits =
                arbeidsforholdService.getAllArbeidssteder(additionalArbeidsforhold, currentTime);

        if (!additionalOrgUnits.isEmpty()) {
            additionalArbeidssteder = additionalOrgUnits
                    .stream()
                    .filter(Optional::isPresent)
                    .map(orgUnit -> orgUnit.get().getOrganisasjonsId().getIdentifikatorverdi())
                    .collect(Collectors.toSet());
        }

        Optional<String> lederPersonalressursLinkOptional = hovedArbeidsstedOptional
                .flatMap(arbeidssted -> ResourceLinkUtil.getOptionalFirstLink(arbeidssted::getLeder));

        //Azure attributes
        Optional<EntraUserAttributes> entraUserAttributes = entraUserService.getEntraUserAttributes(resourceId);
        if (entraUserAttributes.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing azureUserAttributes", resourceId);
            return Optional.empty();
        }
        if(entraUserAttributes.get().entraStatus().equals(UserStatus.DELETED)) {
            log.info("Creating user (student) failed, resourceId={}, user is deleted in Entra", resourceId);
            return Optional.empty();
        }
        String fintStatus = UserUtils.getFINTAnsattStatus(personalressursResource, currentTime);

        return Optional.of(
                createUser(
                        personalressursResource,
                        personResourceOptional.get(),
                        lederPersonalressursLinkOptional.orElse(""),
                        hovedArbeidsstedOptional.isPresent() ? hovedArbeidsstedOptional.get().getNavn() : "mangler info",
                        hovedArbeidsstedOptional.isPresent() ? hovedArbeidsstedOptional.get().getOrganisasjonsId().getIdentifikatorverdi() : "mangler info",
                        additionalArbeidssteder,
                        entraUserAttributes.get(),
                        resourceId,
                        fintStatus)
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
            String resourceId,
            String fintStatus
    ) {

        Date validFrom = personalressursResource.getAnsettelsesperiode().getStart();
        Date validTo = personalressursResource.getAnsettelsesperiode().getSlutt();

        String userType = skoleressursService.isEmployeeInSchool(resourceId)
                ? String.valueOf(UserUtils.UserType.EMPLOYEEFACULTY)
                : String.valueOf(UserUtils.UserType.EMPLOYEESTAFF);

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
                .fintStatus(fintStatus)
                .entraStatus(entraUserAttributes.entraStatus())
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }

}
