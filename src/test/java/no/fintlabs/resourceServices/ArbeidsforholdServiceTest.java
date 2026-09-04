package no.fintlabs.resourceServices;

import no.novari.fint.model.felles.kompleksedatatyper.Identifikator;
import no.novari.fint.model.felles.kompleksedatatyper.Periode;
import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArbeidsforholdServiceTest {

    @Mock
    private GyldighetsperiodeService gyldighetsperiodeService;

    @Mock
    private FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache;

    @Mock
    private FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;

    private ArbeidsforholdService arbeidsforholdService;

    private Date currentTime;
    private String resourceId;

    @BeforeEach
    void setUp() {
        currentTime = new Date();
        resourceId = "testResource123";
        arbeidsforholdService = new ArbeidsforholdService(
                gyldighetsperiodeService,
                arbeidsforholdResourceCache,
                organisasjonselementResourceCache
        );
    }

    @Test
    public void shouldReturnMainEmploymentWhenAvailable() {
        ArbeidsforholdResource hovedArbeidsforhold = createArbeidsforholdResource("1", true, 100);
        ArbeidsforholdResource otherArbeidsforhold = createArbeidsforholdResource("2", false, 50);

        when(gyldighetsperiodeService.isValid(hovedArbeidsforhold.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);
        when(gyldighetsperiodeService.isValid(otherArbeidsforhold.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);

        Optional<ArbeidsforholdResource> result = arbeidsforholdService.getHovedArbeidsforhold(
                List.of(hovedArbeidsforhold, otherArbeidsforhold),
                currentTime,
                resourceId
        );

        assertThat(result).contains(hovedArbeidsforhold);
    }

    @Test
    public void shouldReturnHighestPercentageNonMain() {
        ArbeidsforholdResource higherPercentage = createArbeidsforholdResource("1", false, 80);
        ArbeidsforholdResource lowerPercentage = createArbeidsforholdResource("2", false, 50);

        when(gyldighetsperiodeService.isValid(higherPercentage.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);
        when(gyldighetsperiodeService.isValid(lowerPercentage.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);

        Optional<ArbeidsforholdResource> result = arbeidsforholdService.getHovedArbeidsforhold(
                List.of(higherPercentage, lowerPercentage),
                currentTime,
                resourceId
        );

        assertThat(result).contains(higherPercentage);
    }

    @Test
    public void shouldChooseValidNonMainEmploymentWhenMainEmploymentIsNotValid() {
        ArbeidsforholdResource futureMain = createArbeidsforholdResource("1", true, 100);
        futureMain.setGyldighetsperiode(periode(daysFromNow(10), null));
        ArbeidsforholdResource validNonMain = createArbeidsforholdResource("2", false, 80);
        validNonMain.setGyldighetsperiode(periode(daysFromNow(-1), null));

        when(gyldighetsperiodeService.isValid(futureMain.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(false);
        when(gyldighetsperiodeService.isValid(validNonMain.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);

        Optional<ArbeidsforholdResource> result = arbeidsforholdService.getHovedArbeidsforhold(
                List.of(futureMain, validNonMain),
                currentTime,
                resourceId
        );

        assertThat(result).contains(validNonMain);
    }

    @Test
    public void shouldReturnAllLinkedEmploymentsWithoutDateFiltering() {
        ArbeidsforholdResource currentEmployment = createArbeidsforholdResource("1", true, 100);
        ArbeidsforholdResource futureEmployment = createArbeidsforholdResource("2", false, 50);
        futureEmployment.setGyldighetsperiode(periode(daysFromNow(9), null));

        Collection<Link> arbeidsforholdLinks = Arrays.asList(
                Link.with("systemid/1"),
                Link.with("systemid/2")
        );

        when(arbeidsforholdResourceCache.getOptional("systemid/1"))
                .thenReturn(Optional.of(currentEmployment));
        when(arbeidsforholdResourceCache.getOptional("systemid/2"))
                .thenReturn(Optional.of(futureEmployment));

        ResourceLookupResult<List<ArbeidsforholdResource>> result =
                arbeidsforholdService.resolveAllArbeidsforhold(arbeidsforholdLinks);

        assertThat(result.isFound()).isTrue();
        assertThat(result.resource())
                .hasSize(2)
                .containsExactlyInAnyOrder(currentEmployment, futureEmployment);
    }

    @Test
    public void getArbeidssted_ShouldReturnValidWorkplace() {
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        arbeidsforhold.addArbeidssted(Link.with("organisasjonselementid/1"));

        OrganisasjonselementResource workplace = new OrganisasjonselementResource();
        workplace.setGyldighetsperiode(periode(daysFromNow(9), null));

        when(organisasjonselementResourceCache.getOptional("organisasjonselementid/1"))
                .thenReturn(Optional.of(workplace));
        when(gyldighetsperiodeService.isValid(workplace.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);

        Optional<OrganisasjonselementResource> result = arbeidsforholdService.getArbeidssted(
                arbeidsforhold,
                currentTime
        );

        assertThat(result)
                .isPresent()
                .contains(workplace);
    }

    @Test
    public void resolveArbeidssted_ShouldReturnMissingLinkWhenArbeidsstedHrefIsBlank() {
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        arbeidsforhold.addArbeidssted(Link.with(" "));

        ResourceLookupResult<OrganisasjonselementResource> result =
                arbeidsforholdService.resolveArbeidssted(arbeidsforhold, currentTime);

        assertThat(result.status()).isEqualTo(ResourceLookupStatus.MISSING_LINK);
    }

    @Test
    public void resolveArbeidssted_ShouldReturnCacheMissWhenArbeidsstedIsMissingFromCache() {
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        arbeidsforhold.addArbeidssted(Link.with("organisasjonsId/1"));

        when(organisasjonselementResourceCache.getOptional("organisasjonsid/1"))
                .thenReturn(Optional.empty());

        ResourceLookupResult<OrganisasjonselementResource> result =
                arbeidsforholdService.resolveArbeidssted(arbeidsforhold, currentTime);

        assertThat(result.status()).isEqualTo(ResourceLookupStatus.CACHE_MISS);
    }

    @Test
    public void resolveArbeidssted_ShouldReturnNotValidWhenWorkplaceIsOutsideSelectionWindow() {
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        arbeidsforhold.addArbeidssted(Link.with("organisasjonsId/1"));

        OrganisasjonselementResource workplace = new OrganisasjonselementResource();
        workplace.setGyldighetsperiode(periode(daysFromNow(9), null));

        when(organisasjonselementResourceCache.getOptional("organisasjonsid/1"))
                .thenReturn(Optional.of(workplace));
        when(gyldighetsperiodeService.isValid(workplace.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(false);

        ResourceLookupResult<OrganisasjonselementResource> result =
                arbeidsforholdService.resolveArbeidssted(arbeidsforhold, currentTime);

        assertThat(result.status()).isEqualTo(ResourceLookupStatus.NOT_VALID);
    }

    @Test
    public void getAllArbeidssteder_ShouldReturnAllResolvedWorkplaces() {
        ArbeidsforholdResource arbeidsforhold1 = new ArbeidsforholdResource();
        arbeidsforhold1.addArbeidssted(Link.with("organisasjonselementid/1"));

        ArbeidsforholdResource arbeidsforhold2 = new ArbeidsforholdResource();
        arbeidsforhold2.addArbeidssted(Link.with("organisasjonselementid/2"));

        OrganisasjonselementResource workplace1 = new OrganisasjonselementResource();
        workplace1.setGyldighetsperiode(new Periode());

        OrganisasjonselementResource workplace2 = new OrganisasjonselementResource();
        workplace2.setGyldighetsperiode(new Periode());

        when(organisasjonselementResourceCache.getOptional("organisasjonselementid/1"))
                .thenReturn(Optional.of(workplace1));
        when(organisasjonselementResourceCache.getOptional("organisasjonselementid/2"))
                .thenReturn(Optional.of(workplace2));
        when(gyldighetsperiodeService.isValid(workplace1.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);
        when(gyldighetsperiodeService.isValid(workplace2.getGyldighetsperiode(), currentTime, 0))
                .thenReturn(true);

        List<Optional<OrganisasjonselementResource>> result = arbeidsforholdService.getAllArbeidssteder(
                Arrays.asList(arbeidsforhold1, arbeidsforhold2),
                currentTime
        );

        assertThat(result)
                .hasSize(2)
                .extracting(Optional::get)
                .containsExactly(workplace1, workplace2);
    }

    private ArbeidsforholdResource createArbeidsforholdResource(String id, boolean hovedstilling, long ansettelsesprosent) {
        ArbeidsforholdResource resource = new ArbeidsforholdResource();
        Identifikator identifikator = new Identifikator();
        identifikator.setIdentifikatorverdi(id);
        resource.setHovedstilling(hovedstilling);
        resource.setAnsettelsesprosent(ansettelsesprosent);
        resource.setGyldighetsperiode(new Periode());
        resource.setSystemId(identifikator);
        return resource;
    }

    private Date daysFromNow(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, days);
        return calendar.getTime();
    }

    private Periode periode(Date start, Date slutt) {
        Periode periode = new Periode();
        periode.setStart(start);
        periode.setSlutt(slutt);
        return periode;
    }
}
