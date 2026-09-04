package no.fintlabs.user;

import no.fintlabs.entraUser.EntraUserAttributes;
import no.fintlabs.entraUser.EntraUserService;
import no.fintlabs.resourceServices.ArbeidsforholdService;
import no.fintlabs.resourceServices.PersonService;
import no.fintlabs.resourceServices.PersonalressursService;
import no.fintlabs.resourceServices.ResourceLookupResult;
import no.fintlabs.resourceServices.SkoleressursService;
import no.novari.fint.model.felles.kompleksedatatyper.Identifikator;
import no.novari.fint.model.felles.kompleksedatatyper.Periode;
import no.novari.fint.model.felles.kompleksedatatyper.Personnavn;
import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.novari.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.novari.fint.model.resource.felles.PersonResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPublishingComponentTest {

    @Mock
    private PersonService personService;

    @Mock
    private PersonalressursService personalressursService;

    @Mock
    private EntraUserService entraUserService;

    @Mock
    private ArbeidsforholdService arbeidsforholdService;

    @Mock
    private UserEntityProducerService userEntityProducerService;

    @Mock
    private SkoleressursService skoleressursService;

    @InjectMocks
    private UserPublishingComponent userPublishingComponent;

    @Test
    void createUserShouldPublishInvalidWhenArbeidsforholdLinkIsMissing() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        UUID identityProviderUserObjectId = UUID.randomUUID();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("employee-1"))
                .thenReturn(Optional.of(entraAttributes(identityProviderUserObjectId)));
        when(skoleressursService.isEmployeeInSchool("employee-1")).thenReturn(false);

        Optional<User> result = userPublishingComponent.createUser(personalressursResource);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("employee-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.EMPLOYEESTAFF.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                    assertThat(user.getIdentityProviderUserObjectId()).isEqualTo(identityProviderUserObjectId);
                });
        verifyNoInteractions(personService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenLinkedArbeidsforholdIsMissingFromCache() {
        PersonalressursResource personalressursResource = new PersonalressursResource();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingComponent.createUser(personalressursResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(personService, entraUserService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipInvalidEmployeeWhenEntraUserIsDeleted() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        UUID identityProviderUserObjectId = UUID.randomUUID();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("employee-1"))
                .thenReturn(Optional.of(entraAttributes(identityProviderUserObjectId, UserStatus.DELETED)));

        Optional<User> result = userPublishingComponent.createUser(personalressursResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(personService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldPublishInvalidWhenPersonLinkIsMissing() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforholdResources = List.of(arbeidsforhold);

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforholdResources));
        when(personService.resolvePerson(personalressursResource)).thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("employee-1")).thenReturn(Optional.empty());
        when(skoleressursService.isEmployeeInSchool("employee-1")).thenReturn(false);

        Optional<User> result = userPublishingComponent.createUser(personalressursResource);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("employee-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.EMPLOYEESTAFF.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenLinkedPersonIsMissingFromCache() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforholdResources = List.of(arbeidsforhold);

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforholdResources));
        when(personService.resolvePerson(personalressursResource)).thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingComponent.createUser(personalressursResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenNoValidArbeidsforholdCanBeSelected() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforholdResources = List.of(arbeidsforhold);
        Date currentTime = new Date();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforholdResources));
        when(personService.resolvePerson(personalressursResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(arbeidsforholdService.getHovedArbeidsforhold(arbeidsforholdResources, currentTime, "employee-1"))
                .thenReturn(Optional.empty());

        Optional<User> result = userPublishingComponent.createUser(personalressursResource, currentTime);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldPublishInvalidWhenMainArbeidsstedLinkIsMissing() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource hovedArbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforhold = List.of(hovedArbeidsforhold);
        Date currentTime = new Date();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforhold));
        when(personService.resolvePerson(personalressursResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(arbeidsforholdService.getHovedArbeidsforhold(arbeidsforhold, currentTime, "employee-1"))
                .thenReturn(Optional.of(hovedArbeidsforhold));
        when(arbeidsforholdService.resolveArbeidssted(hovedArbeidsforhold, currentTime))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("employee-1")).thenReturn(Optional.empty());
        when(skoleressursService.isEmployeeInSchool("employee-1")).thenReturn(false);

        Optional<User> result = userPublishingComponent.createUser(personalressursResource, currentTime);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("employee-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.EMPLOYEESTAFF.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenMainArbeidsstedIsMissingFromCache() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource hovedArbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforhold = List.of(hovedArbeidsforhold);
        Date currentTime = new Date();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforhold));
        when(personService.resolvePerson(personalressursResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(arbeidsforholdService.getHovedArbeidsforhold(arbeidsforhold, currentTime, "employee-1"))
                .thenReturn(Optional.of(hovedArbeidsforhold));
        when(arbeidsforholdService.resolveArbeidssted(hovedArbeidsforhold, currentTime))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingComponent.createUser(personalressursResource, currentTime);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenMainArbeidsstedIsNotValid() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        ArbeidsforholdResource hovedArbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforhold = List.of(hovedArbeidsforhold);
        Date currentTime = new Date();

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforhold));
        when(personService.resolvePerson(personalressursResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(arbeidsforholdService.getHovedArbeidsforhold(arbeidsforhold, currentTime, "employee-1"))
                .thenReturn(Optional.of(hovedArbeidsforhold));
        when(arbeidsforholdService.resolveArbeidssted(hovedArbeidsforhold, currentTime))
                .thenReturn(ResourceLookupResult.notValid());

        Optional<User> result = userPublishingComponent.createUser(personalressursResource, currentTime);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, skoleressursService, userEntityProducerService);
    }

    @Test
    void createUserShouldPublishValidEmployeeWithResolvedMainArbeidssted() {
        PersonalressursResource personalressursResource = new PersonalressursResource();
        Date validFrom = new Date(1_000);
        Date validTo = new Date(2_000);
        Date currentTime = new Date();
        personalressursResource.setAnsettelsesperiode(periode(validFrom, validTo));

        ArbeidsforholdResource hovedArbeidsforhold = new ArbeidsforholdResource();
        List<ArbeidsforholdResource> arbeidsforhold = List.of(hovedArbeidsforhold);
        PersonResource person = person("Ada", "Lovelace");
        OrganisasjonselementResource arbeidssted = arbeidssted("Technology", "ORG-1");
        arbeidssted.addLeder(Link.with("systemId/personalressurs/leader-1"));
        UUID identityProviderUserObjectId = UUID.randomUUID();
        EntraUserAttributes entraUserAttributes = new EntraUserAttributes(
                "ada@example.no",
                "ada",
                identityProviderUserObjectId.toString(),
                UserStatus.ACTIVE
        );

        when(personalressursService.getResourceId(personalressursResource)).thenReturn("employee-1");
        when(arbeidsforholdService.resolveAllArbeidsforhold(personalressursResource.getArbeidsforhold()))
                .thenReturn(ResourceLookupResult.found(arbeidsforhold));
        when(personService.resolvePerson(personalressursResource)).thenReturn(ResourceLookupResult.found(person));
        when(arbeidsforholdService.getHovedArbeidsforhold(arbeidsforhold, currentTime, "employee-1"))
                .thenReturn(Optional.of(hovedArbeidsforhold));
        when(arbeidsforholdService.resolveArbeidssted(hovedArbeidsforhold, currentTime))
                .thenReturn(ResourceLookupResult.found(arbeidssted));
        when(arbeidsforholdService.getAllValidArbeidsforholdAsList(arbeidsforhold, currentTime))
                .thenReturn(arbeidsforhold);
        when(arbeidsforholdService.getAllArbeidssteder(arbeidsforhold, currentTime))
                .thenReturn(List.of(Optional.of(arbeidssted)));
        when(entraUserService.getEntraUserAttributes("employee-1"))
                .thenReturn(Optional.of(entraUserAttributes));
        when(skoleressursService.isEmployeeInSchool("employee-1")).thenReturn(true);

        Optional<User> result = userPublishingComponent.createUser(personalressursResource, currentTime);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("employee-1");
                    assertThat(user.getFirstName()).isEqualTo("Ada");
                    assertThat(user.getLastName()).isEqualTo("Lovelace");
                    assertThat(user.getUserType()).isEqualTo(UserType.EMPLOYEEFACULTY.name());
                    assertThat(user.getMainOrganisationUnitName()).isEqualTo("Technology");
                    assertThat(user.getMainOrganisationUnitId()).isEqualTo("ORG-1");
                    assertThat(user.getOrganisationUnitIds()).containsExactly("ORG-1");
                    assertThat(user.getManagerRef()).isEqualTo("systemid/personalressurs/leader-1");
                    assertThat(user.getIdentityProviderUserObjectId()).isEqualTo(identityProviderUserObjectId);
                    assertThat(user.getEmail()).isEqualTo("ada@example.no");
                    assertThat(user.getUserName()).isEqualTo("ada");
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.VALID);
                    assertThat(user.getEntraStatus()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(user.getValidFrom()).isEqualTo(validFrom);
                    assertThat(user.getValidTo()).isEqualTo(validTo);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    private static PersonResource person(String firstName, String lastName) {
        Personnavn navn = new Personnavn();
        navn.setFornavn(firstName);
        navn.setEtternavn(lastName);

        PersonResource person = new PersonResource();
        person.setNavn(navn);
        return person;
    }

    private static OrganisasjonselementResource arbeidssted(String navn, String organisasjonsId) {
        Identifikator identifikator = new Identifikator();
        identifikator.setIdentifikatorverdi(organisasjonsId);

        OrganisasjonselementResource arbeidssted = new OrganisasjonselementResource();
        arbeidssted.setNavn(navn);
        arbeidssted.setOrganisasjonsId(identifikator);
        return arbeidssted;
    }

    private static Periode periode(Date start, Date slutt) {
        Periode periode = new Periode();
        periode.setStart(start);
        periode.setSlutt(slutt);
        return periode;
    }

    private static EntraUserAttributes entraAttributes(UUID identityProviderUserObjectId) {
        return entraAttributes(identityProviderUserObjectId, UserStatus.ACTIVE);
    }

    private static EntraUserAttributes entraAttributes(
            UUID identityProviderUserObjectId,
            String entraStatus
    ) {
        return new EntraUserAttributes(
                "ada@example.no",
                "ada",
                identityProviderUserObjectId.toString(),
                entraStatus
        );
    }
}
