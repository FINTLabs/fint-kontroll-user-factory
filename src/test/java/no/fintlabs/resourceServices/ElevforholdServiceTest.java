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
import static org.mockito.Mockito.when;

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

        Optional<ElevforholdResource> result = elevforholdService.getElevforhold(elevforholdLinks, currentTime);

        assertThat(result).isEmpty();
    }

    @Test
    public void shouldReturnValidElevforhold() {
        Link link = Link.with("systemid/elevforhold/123");
        ElevforholdResource resource = new ElevforholdResource();
        Periode periode = new Periode();
        resource.setGyldighetsperiode(periode);
        when(elevforholdResourceCache.getOptional(anyString())).thenReturn(Optional.of(resource));

        Optional<ElevforholdResource> result = elevforholdService.getElevforhold(List.of(link), currentTime);

        assertThat(result).isPresent().contains(resource);
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

        Optional<ElevforholdResource> result = elevforholdService.getElevforhold(elevforholdLinks, currentTime);

        assertThat(result).isPresent().contains(invalidElevforhold);
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

        Optional<ElevforholdResource> result = elevforholdService.getElevforhold(elevforholdLinks, currentTime);

        assertThat(result).isEmpty();
    }
}