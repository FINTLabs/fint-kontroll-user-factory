package no.fintlabs.resourceServices;

import no.fintlabs.cache.FintCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkoleressursServiceTest {

    @Mock
    private FintCache<String, Long> employeeInSchoolCache;

    @InjectMocks
    private SkoleressursService skoleressursService;

    @Test
    void shouldReturnTrueWhenEmployeeExistsInSchool() {
        String resourceId = "employee123";
        when(employeeInSchoolCache.containsKey(resourceId)).thenReturn(true);

        boolean result = skoleressursService.isEmployeeInSchool(resourceId);

        assertThat(result).isTrue();
        verify(employeeInSchoolCache).containsKey(resourceId);
    }

    @Test
    void shouldReturnFalseWhenEmployeeDoesNotExistInSchool() {
        String resourceId = "employee123";
        when(employeeInSchoolCache.containsKey(resourceId)).thenReturn(false);

        boolean result = skoleressursService.isEmployeeInSchool(resourceId);

        assertThat(result).isFalse();
        verify(employeeInSchoolCache).containsKey(resourceId);
    }
}