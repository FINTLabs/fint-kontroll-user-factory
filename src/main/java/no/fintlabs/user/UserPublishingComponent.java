package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Kontaktinformasjon;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.felles.PersonResource;
import no.fintlabs.azureUser.AzureUserService;
import no.fintlabs.resourceServices.ArbeidsforholdService;
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.resourceServices.PersonService;
import no.fintlabs.resourceServices.PersonalressursService;
import no.fintlabs.resourceServices.SkoleressursService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class UserPublishingComponent {

    private final PersonService personService;
    private final PersonalressursService personalressursService;
    private final AzureUserService azureUserService;
    private final ArbeidsforholdService arbeidsforholdService;
    private final UserEntityProducerService userEntityProducerService;
    private final SkoleressursService skoleressursService;

    public UserPublishingComponent(
            PersonService personService,
            PersonalressursService personalressursService,
            ArbeidsforholdService arbeidsforholdService,
            AzureUserService azureUserService,
            UserEntityProducerService userEntityProducerService,
            SkoleressursService skoleressursService
    ) {
        this.personService = personService;
        this.personalressursService = personalressursService;
        this.arbeidsforholdService = arbeidsforholdService;
        this.azureUserService = azureUserService;
        this.userEntityProducerService = userEntityProducerService;
        this.skoleressursService = skoleressursService;
    }

    @Scheduled(
            initialDelayString = "${fint.kontroll.user.publishing.initial-delay}",
            fixedDelayString = "${fint.kontroll.user.publishing.fixed-delay}"
    )
    public void publishUsers() {
        Date currentTime = Date.from(Instant.now());
        log.info("<< Start scheduled import of employees >>");
        List<PersonalressursResource> allEmployeesWithArbeidsforhold = personalressursService.getAllUsersfromCache();

        List<User> allValidEmployeeUsers = allEmployeesWithArbeidsforhold
                .stream()
                .map(personalressursResource -> createUser(personalressursResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedUsers = userEntityProducerService.publishChangedUsers(allValidEmployeeUsers);
        log.info("Number of personalressurs read from FINT: {}", allEmployeesWithArbeidsforhold.size());
        log.info("Number of users from Entra ID: {}",azureUserService.getNumberOfAzureUsersInCache());
        log.info("Published {} of {} employee users in cache", publishedUsers.size(), allValidEmployeeUsers.size());
        log.info("<< End scheduled import of employees >>");
    }

    private Optional<User> createUser(PersonalressursResource personalressursResource, Date currentTime) {


        String resourceId = personalressursService.getResourceId(personalressursResource);
        boolean isUserOnKafka = UserUtils.isUserAlreadyOnKafka(resourceId);

        Optional<PersonResource> personResourceOptional = personService.getPerson(personalressursResource);
        if (personResourceOptional.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing personResource", resourceId);
            return Optional.empty();
        }

        //Hovedstilling eller stilling med høyest stillingsprosent hvis hovedstilling ikke er spesifisert
        Optional<ArbeidsforholdResource> hovedArbeidsforholdOptional =
                arbeidsforholdService.getHovedArbeidsforhold(personalressursResource.getArbeidsforhold(), currentTime, resourceId);

        if (hovedArbeidsforholdOptional.isEmpty()) {
            log.info("Creating user failed, resourceId={}, missing arbeidsforhold", resourceId);
            return Optional.empty();
        }

        Optional<OrganisasjonselementResource> hovedArbeidsstedOptional = hovedArbeidsforholdOptional
                .flatMap(arbeidsforhold -> arbeidsforholdService.getArbeidssted(arbeidsforhold, currentTime));

        //Additional orgunits
        List<String> additionalArbeidssteder = new ArrayList<>();

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
                    .toList();
        }

        Optional<String> lederPersonalressursLinkOptional = hovedArbeidsstedOptional
                .flatMap(arbeidssted -> ResourceLinkUtil.getOptionalFirstLink(arbeidssted::getLeder));

        //Azure attributes
        Optional<Map<String, String>> azureUserAttributes = azureUserService.getAzureUserAttributes(resourceId);
        if (azureUserAttributes.isEmpty() && !isUserOnKafka) {
            log.info("Creating user failed, resourceId={}, missing azureUserAttributes", resourceId);
            return Optional.empty();
        }
        if (azureUserAttributes.isEmpty()) {
            Map<String, String> attributes = new HashMap<>();
            User userOnKafka = UserUtils.getUserFromKafka(resourceId);
            attributes.put("email", userOnKafka.getEmail());
            attributes.put("userName", userOnKafka.getUserName());
            attributes.put("identityProviderUserObjectId", userOnKafka.getIdentityProviderUserObjectId().toString());
            attributes.put("azureStatus", userOnKafka.getStatus());
            azureUserAttributes = Optional.of(attributes);
        }


        String fintStatus = UserUtils.getFINTAnsattStatus(personalressursResource, currentTime);
        Date statusChanged = fintStatus.equals("ACTIVE")
                ? personalressursResource.getAnsettelsesperiode().getStart()
                : personalressursResource.getAnsettelsesperiode().getSlutt();


        return Optional.of(
                createUser(
                        personalressursResource,
                        personResourceOptional.get(),
                        lederPersonalressursLinkOptional.orElse(""),
                        hovedArbeidsstedOptional.isPresent() ? hovedArbeidsstedOptional.get().getNavn() : "mangler info",
                        hovedArbeidsstedOptional.isPresent() ? hovedArbeidsstedOptional.get().getOrganisasjonsId().getIdentifikatorverdi() : "mangler info",
                        additionalArbeidssteder,
                        azureUserAttributes.get(),
                        resourceId,
                        fintStatus,
                        statusChanged
                )
        );
    }

    private User createUser(
            PersonalressursResource personalressursResource,
            PersonResource personResource,
            String lederPersonalressursHref,
            String organisasjonsnavn,
            String organisasjonsId,
            List<String> additionalArbeidsteder,
            Map<String, String> azureUserAttributes,
            String resourceId,
            String fintStatus,
            Date statusChanged
    ) {


//        String mobilePhone = Optional.ofNullable(personResource.getKontaktinformasjon())
//                .map(Kontaktinformasjon::getMobiltelefonnummer)
//                .orElse("");

        String userStatus = azureUserAttributes.getOrDefault("azureStatus", "").equals("ACTIVE")
                && fintStatus.equals("ACTIVE") ? "ACTIVE" : "DISABLED";

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
                .mobilePhone(null)
                .managerRef(lederPersonalressursHref)
                .identityProviderUserObjectId(UUID.fromString(azureUserAttributes.getOrDefault("identityProviderUserObjectId", "0-0-0-0-0")))
                .email(azureUserAttributes.getOrDefault("email", ""))
                .userName(azureUserAttributes.getOrDefault("userName", ""))
                .status(userStatus)
                .statusChanged(statusChanged)
                .build();
    }

}
