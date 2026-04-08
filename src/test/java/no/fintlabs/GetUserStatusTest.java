package no.fintlabs;

import no.fint.model.felles.kompleksedatatyper.Identifikator;
import no.fint.model.felles.kompleksedatatyper.Periode;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fintlabs.resourceServices.GyldighetsperiodeService;
import no.fintlabs.user.UserStatus;
import no.fintlabs.user.UserUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;


import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GetUserStatusTest {


    @Mock
    private GyldighetsperiodeService gyldighetsperiodeService;


    @Test
    public void shouldReturnActive(){
        // Startdate passed and no enddate
        PersonalressursResource personalressurs = new PersonalressursResource();
        Identifikator ansattnummer = new Identifikator();
        ansattnummer.setIdentifikatorverdi("1003");

        Periode periode = new Periode();
        periode.setBeskrivelse("TEST");
        periode.setStart(Date.from(Instant.parse("2023-01-01T01:00:00Z")));

        personalressurs.setAnsettelsesperiode(periode);
        personalressurs.setAnsattnummer(ansattnummer);
        Date currentTime = Date.from(Instant.now());
        String statusFromGetUserStatus = UserUtils.getFINTAnsattStatus(personalressurs,currentTime);

        assertThat(statusFromGetUserStatus).isEqualTo("ACTIVE");

        System.out.println("getUserStatus returns: " + statusFromGetUserStatus);

    }

    @Test
    public void shouldReturnDisabled(){
        // Startdate and enddate passed
        PersonalressursResource personalressursResource = new PersonalressursResource();
        Identifikator ansattnummer = new Identifikator();
        ansattnummer.setIdentifikatorverdi("1004");

        Periode periode = new Periode();
        periode.setBeskrivelse("Test");
        periode.setStart(Date.from(Instant.parse("2023-01-01T01:00:00Z")));
        periode.setSlutt((Date.from(Instant.parse("2024-01-01T01:00:00Z"))));

        personalressursResource.setAnsettelsesperiode(periode);
        personalressursResource.setAnsattnummer(ansattnummer);
        Date currentTime = Date.from(Instant.now());
        String statusFromGetUserStatus = UserUtils.getFINTAnsattStatus(personalressursResource,currentTime);

        assertThat(statusFromGetUserStatus).isEqualTo(UserStatus.DISABLED);

        System.out.println("getUserStatus returns: " + statusFromGetUserStatus);
    }

}