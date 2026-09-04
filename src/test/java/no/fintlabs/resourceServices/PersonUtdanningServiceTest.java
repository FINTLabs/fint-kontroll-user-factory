package no.fintlabs.resourceServices;

import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.felles.PersonResource;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonUtdanningServiceTest {

    @Mock
    private FintCache<String, PersonResource> personResourceCache;

    @InjectMocks
    private PersonUtdanningService personUtdanningService;

    @Test
    void shouldReturnPersonWhenElevHasValidPersonLink() {
        ElevResource elevResource = new ElevResource();
        elevResource.addPerson(Link.with("systemId/123"));

        PersonResource expectedPerson = new PersonResource();
        when(personResourceCache.getOptional(anyString())).thenReturn(Optional.of(expectedPerson));

        Optional<PersonResource> result = personUtdanningService.getPersonUtdanning(elevResource);

        assertThat(result).isPresent()
                .contains(expectedPerson);
    }

    @Test
    void resolvePersonUtdanningShouldReturnMissingLinkWhenElevHasNoPersonLink() {
        ElevResource elevResource = new ElevResource();

        ResourceLookupResult<PersonResource> result = personUtdanningService.resolvePersonUtdanning(elevResource);

        assertThat(result.status()).isEqualTo(ResourceLookupStatus.MISSING_LINK);
        verify(personResourceCache, never()).getOptional(anyString());
    }

    @Test
    void shouldReturnEmptyOptionalWhenPersonNotFoundInCache() {
        ElevResource elevResource = new ElevResource();
        elevResource.addPerson(Link.with("systemId/123"));

        when(personResourceCache.getOptional(anyString())).thenReturn(Optional.empty());

        Optional<PersonResource> result = personUtdanningService.getPersonUtdanning(elevResource);

        assertThat(result).isEmpty();
    }

    @Test
    void resolvePersonUtdanningShouldReturnCacheMissWhenLinkedPersonIsMissingFromCache() {
        ElevResource elevResource = new ElevResource();
        elevResource.addPerson(Link.with("systemId/123"));

        when(personResourceCache.getOptional("systemid/123")).thenReturn(Optional.empty());

        ResourceLookupResult<PersonResource> result = personUtdanningService.resolvePersonUtdanning(elevResource);

        assertThat(result.status()).isEqualTo(ResourceLookupStatus.CACHE_MISS);
        verify(personResourceCache).getOptional("systemid/123");
    }
}
