package no.fintlabs.resourceServices;

import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.felles.kompleksedatatyper.Periode;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        arbeidsforholdService = new ArbeidsforholdService(gyldighetsperiodeService, arbeidsforholdResourceCache, organisasjonselementResourceCache);
    }

    @Test
    public void shouldReturnMainEmploymentWhenAvailable() {
        ArbeidsforholdResource hovedArbeidsforhold = createArbeidsforholdResource("1", true, 100);
        ArbeidsforholdResource otherArbeidsforhold = createArbeidsforholdResource("2", false, 50);

        Collection<Link> arbeidsforholdLinks = Arrays.asList(
                Link.with("systemid/1"),
                Link.with("systemid/2")
        );

        when(arbeidsforholdResourceCache.getOptional("systemid/1"))
                .thenReturn(Optional.of(hovedArbeidsforhold));
        when(arbeidsforholdResourceCache.getOptional("systemid/2"))
                .thenReturn(Optional.of(otherArbeidsforhold));
        when(gyldighetsperiodeService.isValid(any(), eq(currentTime), eq(0)))
                .thenReturn(true);

        Optional<ArbeidsforholdResource> result = arbeidsforholdService.getHovedArbeidsforhold(
                arbeidsforholdLinks,
                currentTime,
                resourceId
        );

        assertThat(result)
                .isPresent()
                .contains(hovedArbeidsforhold);
    }

    @Test
    public void shouldReturnHighestPercentageNonMain() {
        ArbeidsforholdResource higherPercentage = createArbeidsforholdResource("1", false, 80);
        ArbeidsforholdResource lowerPercentage = createArbeidsforholdResource("2", false, 50);

        Collection<Link> arbeidsforholdLinks = Arrays.asList(
                Link.with("systemid/1"),
                Link.with("systemid/2")
        );

        when(arbeidsforholdResourceCache.getOptional("systemid/1"))
                .thenReturn(Optional.of(higherPercentage));
        when(arbeidsforholdResourceCache.getOptional("systemid/2"))
                .thenReturn(Optional.of(lowerPercentage));
        when(gyldighetsperiodeService.isValid(any(), eq(currentTime), eq(0)))
                .thenReturn(true);

        Optional<ArbeidsforholdResource> result = arbeidsforholdService.getHovedArbeidsforhold(
                arbeidsforholdLinks,
                currentTime,
                resourceId
        );

        assertThat(result)
                .isPresent()
                .contains(higherPercentage);
    }

    @Test
    public void shouldReturnAllValidEmployments() {
        ArbeidsforholdResource valid1 = createArbeidsforholdResource("1", true, 100);
        ArbeidsforholdResource valid2 = createArbeidsforholdResource("2", false, 50);

        Collection<Link> arbeidsforholdLinks = Arrays.asList(
                Link.with("systemid/1"),
                Link.with("systemid/2")
        );

        when(arbeidsforholdResourceCache.getOptional("systemid/1"))
                .thenReturn(Optional.of(valid1));
        when(arbeidsforholdResourceCache.getOptional("systemid/2"))
                .thenReturn(Optional.of(valid2));
        when(gyldighetsperiodeService.isValid(any(), eq(currentTime), eq(0)))
                .thenReturn(true);

        List<ArbeidsforholdResource> result = arbeidsforholdService.getAllValidArbeidsforholdAsList(
                arbeidsforholdLinks,
                currentTime,
                resourceId
        );

        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(valid1, valid2);
    }

    @Test
    public void getArbeidssted_ShouldReturnValidWorkplace() {
        ArbeidsforholdResource arbeidsforhold = new ArbeidsforholdResource();
        arbeidsforhold.addArbeidssted(Link.with("organisasjonselementid/1"));

        OrganisasjonselementResource workplace = new OrganisasjonselementResource();
        workplace.setGyldighetsperiode(new Periode());

        when(organisasjonselementResourceCache.getOptional("organisasjonselementid/1"))
                .thenReturn(Optional.of(workplace));
        when(gyldighetsperiodeService.isValid(any(), eq(currentTime), eq(0)))
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
    public void getAllArbeidssteder_ShouldReturnAllValidWorkplaces() {
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
        when(gyldighetsperiodeService.isValid(any(), eq(currentTime), eq(0)))
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
}