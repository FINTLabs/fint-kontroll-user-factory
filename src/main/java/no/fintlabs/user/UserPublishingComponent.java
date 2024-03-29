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
            UserEntityProducerService userEntityProducerService, SkoleressursService skoleressursService
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
        List<User> allUsersInCache = personalressursService.getAllUsersfromCache()
                .stream()
                .map(personalressursResource -> createUser(personalressursResource, currentTime))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<User> publishedUsers = userEntityProducerService.publishChangedUsers(allUsersInCache);

        log.info("Published {} of {} users in cache", publishedUsers.size(), allUsersInCache.size());
    }

    private Optional<User> createUser(PersonalressursResource personalressursResource, Date currentTime) {

        Optional<PersonResource> personResourceOptional = personService.getPerson(personalressursResource);
        if (personResourceOptional.isEmpty()) {
            return Optional.empty();
        }

        //Hovedstilling
        Optional<ArbeidsforholdResource> arbeidsforholdOptional =
                arbeidsforholdService.getHovedArbeidsforhold(personalressursResource.getArbeidsforhold(), currentTime);
        if (arbeidsforholdOptional.isEmpty()) {
            return Optional.empty();
        }
        Optional<OrganisasjonselementResource> hovedArbeidsstedOptional = arbeidsforholdOptional
                .flatMap(arbeidsforhold -> arbeidsforholdService.getArbeidssted(arbeidsforhold, currentTime));



        //Additional orgunits
        List<String> additionalArbeidssteder = new ArrayList<>();

            List<ArbeidsforholdResource> additionalArbeidsforhold =
                    arbeidsforholdService.getAllValidArbeidsforholdAsList(personalressursResource.getArbeidsforhold(),
                            currentTime);

            List<Optional<OrganisasjonselementResource>> additionalOrgUnits =
                    arbeidsforholdService.getAllArbeidssteder(additionalArbeidsforhold, currentTime);

            if (!additionalOrgUnits.isEmpty()){
                additionalArbeidssteder = additionalOrgUnits
                        .stream()
                        .filter(Optional::isPresent)
                        .map(orgUnit -> orgUnit.get().getOrganisasjonsId().getIdentifikatorverdi())
                        .toList();
            }


        Optional<String> lederPersonalressursLinkOptional = hovedArbeidsstedOptional
                .flatMap(arbeidssted -> ResourceLinkUtil.getOptionalFirstLink(arbeidssted::getLeder));

        //Azure attributes
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(personalressursResource);
        String resourceId = hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") +1);
        Optional<Map<String,String>> azureUserAttributes = azureUserService.getAzureUserAttributes(resourceId);
        if (azureUserAttributes.isEmpty()){
            return Optional.empty();
        }

        String fintStatus = UserUtils.getUserStatus(personalressursResource,currentTime );
        Date statusChanged = fintStatus.equals("ACTIV")
                ?personalressursResource.getAnsettelsesperiode().getStart()
                :personalressursResource.getAnsettelsesperiode().getSlutt();


        return Optional.of(
                createUser(
                        personalressursResource,
                        personResourceOptional.get(),
                        lederPersonalressursLinkOptional.orElse(""),
                        hovedArbeidsstedOptional.isPresent()? hovedArbeidsstedOptional.get().getNavn():"mangler info",
                        hovedArbeidsstedOptional.isPresent()? hovedArbeidsstedOptional.get().getOrganisasjonsId().getIdentifikatorverdi() :"mangler info",
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
            Map<String,String> azureUserAttributes,
            String resourceId,
            String fintStatus,
            Date statusChanged
    ) {

        //log.info("**** orgunitId: " + organisasjonsId + " :: orgUnitName: " + organisasjonsnavn + " :: resourcID:" + resourceId + " ****");

        String mobilePhone = Optional.ofNullable(personResource.getKontaktinformasjon())
                .map(Kontaktinformasjon::getMobiltelefonnummer)
                .orElse("");

        String userStatus = azureUserAttributes.getOrDefault("azureStatus","").equals("ACTIV")
                && fintStatus.equals("ACTIV")?"ACTIV":"DISABLED";

        String userType = skoleressursService.isEmployeeInSchool(resourceId)
                ?String.valueOf(UserUtils.UserType.EMPLOYEEFACULTY)
                :String.valueOf(UserUtils.UserType.EMPLOYEESTAFF);




        return User
                .builder()
                .resourceId(resourceId)
                .firstName(personResource.getNavn().getFornavn())
                .lastName(personResource.getNavn().getEtternavn())
                .userType(userType)
                .mainOrganisationUnitName(organisasjonsnavn)
                .mainOrganisationUnitId(organisasjonsId)
                .organisationUnitIds(additionalArbeidsteder)
                .mobilePhone(mobilePhone)
                .managerRef(lederPersonalressursHref)
                .identityProviderUserObjectId(UUID.fromString(azureUserAttributes.getOrDefault("identityProviderUserObjectId","0-0-0-0-0")))
                .email(azureUserAttributes.getOrDefault("email",""))
                .userName(azureUserAttributes.getOrDefault("userName",""))
                .status(userStatus)
                .statusChanged(statusChanged)
                .build();
    }

}
