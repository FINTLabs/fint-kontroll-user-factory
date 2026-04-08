package no.fintlabs.resourceServices;

import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.Link;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalressursServiceTest {

    @Mock
    private FintCache<String, PersonalressursResource> PersonalressursResourceCache;

    @InjectMocks
    private PersonalressursService personalressursService;
    
    @Test
    public void shouldGetAllUsersfromCache() {
        PersonalressursResource withArbeidsforhold = new PersonalressursResource();
        withArbeidsforhold.addArbeidsforhold(Link.with("systemId/arbeidsforhold/123"));

        PersonalressursResource withoutArbeidsforhold = new PersonalressursResource();

        PersonalressursResource withMultipleArbeidsforhold = new PersonalressursResource();
        withMultipleArbeidsforhold.addArbeidsforhold(Link.with("systemId/arbeidsforhold/456"));
        withMultipleArbeidsforhold.addArbeidsforhold(Link.with("systemId/arbeidsforhold/789"));

        when(PersonalressursResourceCache.getAllDistinct()).thenReturn(Arrays.asList(
                withArbeidsforhold,
                withoutArbeidsforhold,
                withMultipleArbeidsforhold
        ));

        List<PersonalressursResource> result = personalressursService.getAllUsersfromCache();

        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(withArbeidsforhold, withMultipleArbeidsforhold)
                .doesNotContain(withoutArbeidsforhold);

        verify(PersonalressursResourceCache).getAllDistinct();
    }
    
}