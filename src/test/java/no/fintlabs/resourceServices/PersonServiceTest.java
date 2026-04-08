package no.fintlabs.resourceServices;

import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.felles.PersonResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.NoSuchLinkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private FintCache<String, PersonResource> personResourceCache;

    @InjectMocks
    private PersonService personService;

    @Test
    void shouldReturnPersonWhenPersonalRessursHasValidPersonLink() {
        PersonalressursResource personalressurs = new PersonalressursResource();
        personalressurs.addPerson(Link.with("systemId/123"));

        PersonResource expectedPerson = new PersonResource();
        when(personResourceCache.getOptional("systemid/123")).thenReturn(Optional.of(expectedPerson));

        Optional<PersonResource> result = personService.getPerson(personalressurs);

        assertThat(result)
                .isPresent()
                .contains(expectedPerson);
        verify(personResourceCache).getOptional("systemid/123");
    }

    @Test
    void shouldReturnEmptyOptionalWhenPersonNotFoundInCache() {
        PersonalressursResource personalressurs = new PersonalressursResource();
        personalressurs.addPerson(Link.with("systemId/123"));

        when(personResourceCache.getOptional("systemid/123")).thenReturn(Optional.empty());

        Optional<PersonResource> result = personService.getPerson(personalressurs);

        assertThat(result).isEmpty();
        verify(personResourceCache).getOptional("systemid/123");
    }

    @Test
    void shouldThrowNoSuchLinkExceptionWhenPersonalressursHasNoPersonLink() {
        PersonalressursResource personalressurs = new PersonalressursResource();

        assertThatExceptionOfType(NoSuchLinkException.class)
                .isThrownBy(() -> personService.getPerson(personalressurs))
                .withMessageContaining("No link for 'Person'");

        verify(personResourceCache, never()).getOptional(anyString());
    }

    @Test
    void shouldHandleMultiplePersonLinksAndUseFirstOne() {
        PersonalressursResource personalressurs = new PersonalressursResource();
        personalressurs.addPerson(Link.with("systemId/123"));
        personalressurs.addPerson(Link.with("systemId/456"));

        PersonResource expectedPerson = new PersonResource();
        when(personResourceCache.getOptional("systemid/123")).thenReturn(Optional.of(expectedPerson));

        Optional<PersonResource> result = personService.getPerson(personalressurs);

        assertThat(result)
                .isPresent()
                .contains(expectedPerson);
        verify(personResourceCache).getOptional("systemid/123");
        verify(personResourceCache, never()).getOptional("systemid/456");
    }
}
