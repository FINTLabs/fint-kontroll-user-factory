package no.fintlabs.resourceServices;

import no.fint.model.felles.kompleksedatatyper.Periode;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElevforholdServiceTest {

    @Mock
    private FintCache<String, ElevforholdResource> elevforholdResourceCache;

    @Mock
    private FintCache<String, SkoleResource> skoleResourceCache;

    @Mock
    private FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;

    private  ElevforholdService elevforholdService;

    private Date currentTime;

    @BeforeEach
    void init() {
        currentTime = new Date();
        elevforholdService = new ElevforholdService(elevforholdResourceCache, skoleResourceCache, organisasjonselementResourceCache);
    }

    @Test
    public void shouldReturnEmptyWhenNoValidElevforholdLinks() {
        List<Link> elevforholdLinks = Collections.emptyList();

        List<ElevforholdResource> result = elevforholdService.getAllForElev(elevforholdLinks);

        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnValidElevforhold() {
        Link link = Link.with("systemid/elevforhold/123");
        ElevforholdResource resource = new ElevforholdResource();
        Periode periode = new Periode();
        resource.setGyldighetsperiode(periode);
        when(elevforholdResourceCache.getOptional(anyString())).thenReturn(Optional.of(resource));

        List<ElevforholdResource> result = elevforholdService.getAllForElev(List.of(link));

        assertThat(result).contains(resource);
    }


    @Test
    public void shouldReturnEvenThoughElevforholdNotValid() {
        Link link = Link.with("systemId/elevforhold/123");
        List<Link> elevforholdLinks = Collections.singletonList(link);

        ElevforholdResource invalidElevforhold = new ElevforholdResource();
        Periode periode = new Periode();
        invalidElevforhold.setGyldighetsperiode(periode);

        when(elevforholdResourceCache.getOptional("systemid/elevforhold/123"))
                .thenReturn(Optional.of(invalidElevforhold));

        List<ElevforholdResource> result = elevforholdService.getAllForElev(elevforholdLinks);

        assertThat(result).contains(invalidElevforhold);
    }

    @Test
    public void shouldReturnSkoleWhenValidElevforhold() {
        ElevforholdResource elevforhold = new ElevforholdResource();
        elevforhold.addSkole(Link.with("systemId/skole/123"));

        SkoleResource expectedSkole = new SkoleResource();
        when(skoleResourceCache.getOptional("systemid/skole/123"))
                .thenReturn(Optional.of(expectedSkole));

        Optional<SkoleResource> result = elevforholdService.getSkole(elevforhold);

        assertThat(result).isPresent().contains(expectedSkole);
    }

    @Test
    public void shouldReturnSkoleOrgUnit() {
        SkoleResource skoleResource = new SkoleResource();
        skoleResource.addOrganisasjon(Link.with("organisasjonsKode/123"));

        OrganisasjonselementResource expectedOrgUnit = new OrganisasjonselementResource();
        when(organisasjonselementResourceCache.get(anyString()))
                .thenReturn(expectedOrgUnit);

        Optional<OrganisasjonselementResource> result = elevforholdService.getSkoleOrgUnit(skoleResource);

        assertThat(result).isPresent().contains(expectedOrgUnit);
    }

    @Test
    public void shouldReturnEmptyWhenNoValidPeriode() {

        Link link = Link.with("systemId/elevforhold/123");
        List<Link> elevforholdLinks = Collections.singletonList(link);

        ElevforholdResource elevforholdWithoutPeriode = new ElevforholdResource();
        when(elevforholdResourceCache.getOptional("systemid/elevforhold/123"))
                .thenReturn(Optional.of(elevforholdWithoutPeriode));

        List<ElevforholdResource> result = elevforholdService.getAllForElev(elevforholdLinks);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllForElev_shouldReturnOnlyPresentAndWithGyldighetsperiode() {
        ElevforholdResource withPeriod = elevforhold(daysFromNow(-10),
                daysFromNow(10)
        );
        ElevforholdResource withoutPeriod = elevforholdWithoutPeriod();

        List<Link> links = List.of(link("e1"), link("e2"), link("missing"));

        when(elevforholdResourceCache.getOptional("e1")).thenReturn(Optional.of(withPeriod));
        when(elevforholdResourceCache.getOptional("e2")).thenReturn(Optional.of(withoutPeriod));
        when(elevforholdResourceCache.getOptional("missing")).thenReturn(Optional.empty());

        List<ElevforholdResource> result = elevforholdService.getAllForElev(links);

        assertThat(result)
                .hasSize(1)
                .containsExactly(withPeriod);
        verify(elevforholdResourceCache, times(1)).getOptional("e1");
        verify(elevforholdResourceCache, times(1)).getOptional("e2");
        verify(elevforholdResourceCache, times(1)).getOptional("missing");
    }

    @Test
    void getAllForElev_shouldReturnEmptyWhenAllMissingOrWithoutPeriod() {
        ElevforholdResource withoutPeriod = elevforholdWithoutPeriod();
        List<Link> links = List.of(link("e2"), link("missing"));

        when(elevforholdResourceCache.getOptional("e2")).thenReturn(Optional.of(withoutPeriod));
        when(elevforholdResourceCache.getOptional("missing")).thenReturn(Optional.empty());

        List<ElevforholdResource> result = elevforholdService.getAllForElev(links);

        assertThat(result).isEmpty();
    }


    @Test
    void getMainElevforhold_shouldReturnActiveNowIfPresent() {
        ElevforholdResource activeNow = elevforhold(daysFromNow(-1), daysFromNow(1));

        ElevforholdResource future = elevforhold(daysFromNow(10), daysFromNow(20));

        Optional<ElevforholdResource> result = elevforholdService.getMainElevforhold(
                List.of(future, activeNow), currentTime);

        assertThat(result).contains(activeNow);
    }

    @Test
    void getMainElevforhold_shouldFallbackToFirstWhenNoneActive() {
        ElevforholdResource past = elevforhold(daysFromNow(-20), daysFromNow(-10));
        ElevforholdResource future = elevforhold(daysFromNow(10), daysFromNow(20));

        Optional<ElevforholdResource> result = elevforholdService.getMainElevforhold(
                List.of(past, future), currentTime);

        assertThat(result).contains(past);
    }
    private static Link link(String href) {
        Link l = new Link();
        l.setVerdi(href);
        return l;
    }

    private static ElevforholdResource elevforhold(Date start, Date end) {
        ElevforholdResource res = new ElevforholdResource();
        res.setGyldighetsperiode(periode(start, end));
        return res;
    }

    private static ElevforholdResource elevforholdWithoutPeriod() {
        ElevforholdResource res = new ElevforholdResource();
        res.setGyldighetsperiode(null);
        return res;
    }

    private static Periode periode(Date start, Date end) {
        Periode p = new Periode();
        p.setStart(start);
        p.setSlutt(end);
        return p;
    }

    private static Date daysFromNow(int days) {
        return Date.from(
                java.time.Instant.now().plus(java.time.Period.ofDays(days))
        );
    }

    @Test
    void getMainElevforhold_shouldReturnEmptyForEmptyList() {
        Optional<ElevforholdResource> result = elevforholdService.getMainElevforhold(
                Collections.emptyList(), currentTime);

        assertThat(result).isEmpty();
    }
}