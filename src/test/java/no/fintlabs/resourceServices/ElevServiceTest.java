
package no.fintlabs.resourceServices;

import no.fint.model.resource.Link;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElevServiceTest {

    @Mock
    private FintCache<String, ElevResource> elevResourceCache;

    @Mock
    private GyldighetsperiodeService gyldighetsperiodeService;

    @InjectMocks
    private ElevService elevService;

    @Test
    void shouldFilterOutElevsWithoutElevforhold() {
        ElevResource elevWithoutElevforhold = new ElevResource();
        ElevResource elevWithElevforhold = new ElevResource();
        elevWithElevforhold.addElevforhold(Link.with("elevforhold/systemId/123"));

        when(elevResourceCache.getAllDistinct()).thenReturn(Arrays.asList(
                elevWithoutElevforhold,
                elevWithElevforhold
        ));

        Date currentTime = new Date();

        List<ElevResource> result = elevService.getAllEleverWithElevforhold(currentTime);

        assertThat(result)
                .hasSize(1)
                .containsExactly(elevWithElevforhold);
    }

    @Test
    void shouldReturnAllElevsWithElevforhold() {
        ElevResource elev1 = new ElevResource();
        elev1.addElevforhold(Link.with("elevforhold/systemId/123"));

        ElevResource elev2 = new ElevResource();
        elev2.addElevforhold(Link.with("elevforhold/systemId/456"));

        when(elevResourceCache.getAllDistinct()).thenReturn(Arrays.asList(elev1, elev2));

        Date currentTime = new Date();

        List<ElevResource> result = elevService.getAllEleverWithElevforhold(currentTime);

        assertThat(result)
                .hasSize(2)
                .containsExactlyInAnyOrder(elev1, elev2);
    }

    @Test
    void shouldHandleElevWithMultipleElevforhold() {
        ElevResource elevWithMultipleElevforhold = new ElevResource();
        elevWithMultipleElevforhold.addElevforhold(Link.with("elevforhold/systemId/123"));
        elevWithMultipleElevforhold.addElevforhold(Link.with("elevforhold/systemId/456"));

        when(elevResourceCache.getAllDistinct()).thenReturn(Collections.singletonList(elevWithMultipleElevforhold));

        Date currentTime = new Date();

        List<ElevResource> result = elevService.getAllEleverWithElevforhold(currentTime);

        assertThat(result)
                .hasSize(1)
                .containsExactly(elevWithMultipleElevforhold);
    }
}