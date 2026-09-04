package no.fintlabs.user;

import no.fintlabs.entraUser.EntraUserAttributes;
import no.fintlabs.entraUser.EntraUserService;
import no.fintlabs.resourceServices.ElevService;
import no.fintlabs.resourceServices.ElevforholdService;
import no.fintlabs.resourceServices.PersonUtdanningService;
import no.fintlabs.resourceServices.ResourceLookupResult;
import no.novari.fint.model.felles.kompleksedatatyper.Identifikator;
import no.novari.fint.model.felles.kompleksedatatyper.Periode;
import no.novari.fint.model.felles.kompleksedatatyper.Personnavn;
import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.felles.PersonResource;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.novari.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.novari.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPublishingElevComponentTest {

    @Mock
    private ElevService elevService;

    @Mock
    private PersonUtdanningService personUtdanningService;

    @Mock
    private ElevforholdService elevforholdService;

    @Mock
    private EntraUserService entraUserService;

    @Mock
    private UserEntityProducerService userEntityProducerService;

    @InjectMocks
    private UserPublishingElevComponent userPublishingElevComponent;

    @Test
    void createUserShouldPublishInvalidWhenPersonLinkIsMissing() {
        ElevResource elevResource = elev("student-1");
        UUID identityProviderUserObjectId = UUID.randomUUID();

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("student-1"))
                .thenReturn(Optional.of(entraAttributes(identityProviderUserObjectId)));

        Optional<User> result = userPublishingElevComponent.createUser(elevResource);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("student-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.STUDENT.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                    assertThat(user.getIdentityProviderUserObjectId()).isEqualTo(identityProviderUserObjectId);
                });
        verifyNoInteractions(elevforholdService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenLinkedPersonIsMissingFromCache() {
        ElevResource elevResource = elev("student-1");

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(elevforholdService, entraUserService, userEntityProducerService);
    }

    @Test
    void createUserShouldSkipInvalidStudentWhenEntraUserIsDeleted() {
        ElevResource elevResource = elev("student-1");
        UUID identityProviderUserObjectId = UUID.randomUUID();

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("student-1"))
                .thenReturn(Optional.of(entraAttributes(identityProviderUserObjectId, UserStatus.DELETED)));

        Optional<User> result = userPublishingElevComponent.createUser(elevResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(elevforholdService, userEntityProducerService);
    }

    @Test
    void createUserShouldPublishInvalidWhenElevforholdLinkIsMissing() {
        ElevResource elevResource = elev("student-1");

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("student-1")).thenReturn(Optional.empty());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("student-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.STUDENT.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenLinkedElevforholdIsMissingFromCache() {
        ElevResource elevResource = elevWithElevforhold("student-1");

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, userEntityProducerService);
    }

    @Test
    void createUserShouldUseMainElevforholdForSchoolOrgUnit() {
        ElevResource elevResource = elevWithElevforhold("student-1");
        Date currentTime = new Date();
        Date validFrom = new Date(1_000);
        Date validTo = new Date(2_000);
        ElevforholdResource firstElevforhold = elevforhold(validFrom, validTo);
        ElevforholdResource mainElevforhold = elevforhold(new Date(3_000), new Date(4_000));
        List<ElevforholdResource> elevforhold = List.of(firstElevforhold, mainElevforhold);
        PersonResource person = person("Ada", "Lovelace");
        SkoleResource skole = new SkoleResource();
        OrganisasjonselementResource orgUnit = orgUnit("Main school", "SCHOOL-1");
        UUID identityProviderUserObjectId = UUID.randomUUID();
        EntraUserAttributes entraUserAttributes = new EntraUserAttributes(
                "ada@example.no",
                "ada",
                identityProviderUserObjectId.toString(),
                UserStatus.ACTIVE
        );

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(person));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.found(elevforhold));
        when(elevforholdService.getMainElevforhold(elevforhold, currentTime))
                .thenReturn(Optional.of(mainElevforhold));
        when(elevforholdService.resolveSkole(mainElevforhold))
                .thenReturn(ResourceLookupResult.found(skole));
        when(elevforholdService.resolveSkoleOrgUnit(skole))
                .thenReturn(ResourceLookupResult.found(orgUnit));
        when(entraUserService.getEntraUserAttributes("student-1"))
                .thenReturn(Optional.of(entraUserAttributes));

        Optional<User> result = userPublishingElevComponent.createUser(elevResource, currentTime);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("student-1");
                    assertThat(user.getFirstName()).isEqualTo("Ada");
                    assertThat(user.getLastName()).isEqualTo("Lovelace");
                    assertThat(user.getUserType()).isEqualTo(UserType.STUDENT.name());
                    assertThat(user.getMainOrganisationUnitName()).isEqualTo("Main school");
                    assertThat(user.getMainOrganisationUnitId()).isEqualTo("SCHOOL-1");
                    assertThat(user.getIdentityProviderUserObjectId()).isEqualTo(identityProviderUserObjectId);
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.VALID);
                    assertThat(user.getEntraStatus()).isEqualTo(UserStatus.ACTIVE);
                    assertThat(user.getValidFrom()).isEqualTo(validFrom);
                    assertThat(user.getValidTo()).isEqualTo(new Date(4_000));
                });
        verify(elevforholdService, never()).resolveSkole(firstElevforhold);
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldPublishInvalidWhenSelectedElevforholdHasNoSkoleLink() {
        ElevResource elevResource = elevWithElevforhold("student-1");
        Date currentTime = new Date();
        ElevforholdResource mainElevforhold = elevforhold(new Date(1_000), new Date(2_000));
        List<ElevforholdResource> elevforhold = List.of(mainElevforhold);

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.found(elevforhold));
        when(elevforholdService.getMainElevforhold(elevforhold, currentTime))
                .thenReturn(Optional.of(mainElevforhold));
        when(elevforholdService.resolveSkole(mainElevforhold))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("student-1")).thenReturn(Optional.empty());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource, currentTime);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("student-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.STUDENT.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenSelectedSkoleIsMissingFromCache() {
        ElevResource elevResource = elevWithElevforhold("student-1");
        Date currentTime = new Date();
        ElevforholdResource mainElevforhold = elevforhold(new Date(1_000), new Date(2_000));
        List<ElevforholdResource> elevforhold = List.of(mainElevforhold);

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.found(elevforhold));
        when(elevforholdService.getMainElevforhold(elevforhold, currentTime))
                .thenReturn(Optional.of(mainElevforhold));
        when(elevforholdService.resolveSkole(mainElevforhold))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource, currentTime);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, userEntityProducerService);
    }

    @Test
    void createUserShouldPublishInvalidWhenSelectedSkoleHasNoOrgUnitLink() {
        ElevResource elevResource = elevWithElevforhold("student-1");
        Date currentTime = new Date();
        ElevforholdResource mainElevforhold = elevforhold(new Date(1_000), new Date(2_000));
        List<ElevforholdResource> elevforhold = List.of(mainElevforhold);
        SkoleResource skole = new SkoleResource();

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.found(elevforhold));
        when(elevforholdService.getMainElevforhold(elevforhold, currentTime))
                .thenReturn(Optional.of(mainElevforhold));
        when(elevforholdService.resolveSkole(mainElevforhold))
                .thenReturn(ResourceLookupResult.found(skole));
        when(elevforholdService.resolveSkoleOrgUnit(skole))
                .thenReturn(ResourceLookupResult.missingLink());
        when(entraUserService.getEntraUserAttributes("student-1")).thenReturn(Optional.empty());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource, currentTime);

        assertThat(result)
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getResourceId()).isEqualTo("student-1");
                    assertThat(user.getUserType()).isEqualTo(UserType.STUDENT.name());
                    assertThat(user.getFintStatus()).isEqualTo(FintStatus.INVALID);
                });
        verifyNoInteractions(userEntityProducerService);
    }

    @Test
    void createUserShouldSkipWhenSelectedOrgUnitIsMissingFromCache() {
        ElevResource elevResource = elevWithElevforhold("student-1");
        Date currentTime = new Date();
        ElevforholdResource mainElevforhold = elevforhold(new Date(1_000), new Date(2_000));
        List<ElevforholdResource> elevforhold = List.of(mainElevforhold);
        SkoleResource skole = new SkoleResource();

        when(personUtdanningService.resolvePersonUtdanning(elevResource))
                .thenReturn(ResourceLookupResult.found(new PersonResource()));
        when(elevforholdService.resolveAllForElev(elevResource.getElevforhold()))
                .thenReturn(ResourceLookupResult.found(elevforhold));
        when(elevforholdService.getMainElevforhold(elevforhold, currentTime))
                .thenReturn(Optional.of(mainElevforhold));
        when(elevforholdService.resolveSkole(mainElevforhold))
                .thenReturn(ResourceLookupResult.found(skole));
        when(elevforholdService.resolveSkoleOrgUnit(skole))
                .thenReturn(ResourceLookupResult.cacheMiss());

        Optional<User> result = userPublishingElevComponent.createUser(elevResource, currentTime);

        assertThat(result).isEmpty();
        verifyNoInteractions(entraUserService, userEntityProducerService);
    }

    private static ElevResource elev(String resourceId) {
        ElevResource elevResource = new ElevResource();
        elevResource.addSelf(Link.with("systemId/elev/" + resourceId));
        return elevResource;
    }

    private static ElevResource elevWithElevforhold(String resourceId) {
        ElevResource elevResource = elev(resourceId);
        elevResource.addElevforhold(Link.with("systemId/elevforhold/" + resourceId));
        return elevResource;
    }

    private static ElevforholdResource elevforhold(Date validFrom, Date validTo) {
        ElevforholdResource elevforhold = new ElevforholdResource();
        elevforhold.setGyldighetsperiode(periode(validFrom, validTo));
        return elevforhold;
    }

    private static Periode periode(Date start, Date slutt) {
        Periode periode = new Periode();
        periode.setStart(start);
        periode.setSlutt(slutt);
        return periode;
    }

    private static PersonResource person(String firstName, String lastName) {
        Personnavn navn = new Personnavn();
        navn.setFornavn(firstName);
        navn.setEtternavn(lastName);

        PersonResource person = new PersonResource();
        person.setNavn(navn);
        return person;
    }

    private static OrganisasjonselementResource orgUnit(String navn, String organisasjonsId) {
        Identifikator identifikator = new Identifikator();
        identifikator.setIdentifikatorverdi(organisasjonsId);

        OrganisasjonselementResource orgUnit = new OrganisasjonselementResource();
        orgUnit.setNavn(navn);
        orgUnit.setOrganisasjonsId(identifikator);
        return orgUnit;
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
