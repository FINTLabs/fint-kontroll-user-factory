package no.fintlabs.resourceServices;

import no.fint.model.resource.Link;
import no.fint.model.resource.felles.PersonResource;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

 /*   @Test
    void shouldReturnEmptyOptionalWhenElevHasNoPersonLink() {
        ElevResource elevResource = new ElevResource();

        Optional<PersonResource> result = personUtdanningService.getPersonUtdanning(elevResource);

        assertThat(result).isEmpty();
    }
*/
    @Test
    void shouldReturnEmptyOptionalWhenPersonNotFoundInCache() {
        ElevResource elevResource = new ElevResource();
        elevResource.addPerson(Link.with("systemId/123"));

        when(personResourceCache.getOptional(anyString())).thenReturn(Optional.empty());

        Optional<PersonResource> result = personUtdanningService.getPersonUtdanning(elevResource);

        assertThat(result).isEmpty();
    }
}