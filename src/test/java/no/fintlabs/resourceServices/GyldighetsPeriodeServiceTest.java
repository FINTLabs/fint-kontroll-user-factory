package no.fintlabs.resourceServices;

import no.fint.model.felles.kompleksedatatyper.Periode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import no.fintlabs.resourceServices.GyldighetsperiodeService.NullPeriodeException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;


class GyldighetsPeriodeServiceTest {


    private GyldighetsperiodeService gyldighetsperiodeService;
    private Date currentTime;

    private static final int daysBeforeStart= 10;

    @BeforeEach
    public void initEachTest(){
        gyldighetsperiodeService = new GyldighetsperiodeService();
        currentTime = Date.from(LocalDate.of(2020,10,31)
                .atStartOfDay(ZoneId.of("Z")).toInstant());
    }


    @Test
    public void shouldReturnCorrectDateWhenDaysBeforeStartAreSet(){
        Date initialDate = Date.from(LocalDate.of(2024, 1, 1)
                .atStartOfDay(ZoneId.of("Z")).toInstant());
        System.out.println("Initial start date : " + initialDate);
        Date resheduledDate = gyldighetsperiodeService.getStartDate(initialDate,30);
        System.out.println("Rescheduled start date: " + resheduledDate);

        Date dateToCheck = Date.from(LocalDate.of(2023,12,2)
                .atStartOfDay(ZoneId.of("Z")).toInstant());

        System.out.println("Date for check : " + dateToCheck);

        boolean valid = resheduledDate.equals(dateToCheck);

        assertTrue(valid);
    }

    @Test
    public void shouldReturnTrueGyldighetsperiodeNotNullCurrentTimeBetweenStartAndSluttDate(){
        Date startDate = Date.from((LocalDate.of(2020,8,1)
                .atStartOfDay(ZoneId.of("Z")).toInstant()));
        Date sluttDate = Date.from(LocalDate.of(2020,12,31)
                .atStartOfDay(ZoneId.of("Z")).toInstant());
        Periode gyldighetsPeriode = new Periode();
        gyldighetsPeriode.setSlutt(sluttDate);
        gyldighetsPeriode.setStart(startDate);

        boolean valid = gyldighetsperiodeService.isValid(gyldighetsPeriode,currentTime,daysBeforeStart);

        assertTrue(valid);

    }

    @Test
    public void shouldThrowNullPeriodeExeptionGyldihetsperiodeIsNull(){

        assertThrows(NullPeriodeException.class, () -> gyldighetsperiodeService.isValid(null
                ,currentTime,daysBeforeStart));

    }


    @Test
    public void shouldReturnFalseGyldighetsperiodeNotNullCurrentTimeAfterEndDate(){
        Date startDate = Date.from((LocalDate.of(2020,8,1)
                .atStartOfDay(ZoneId.of("Z")).toInstant()));
        Date sluttDate = Date.from(LocalDate.of(2020,9,28)
                .atStartOfDay(ZoneId.of("Z")).toInstant());
        Periode gyldighetsPeriode = new Periode();
        gyldighetsPeriode.setSlutt(sluttDate);
        gyldighetsPeriode.setStart(startDate);

        boolean valid = gyldighetsperiodeService.isValid(gyldighetsPeriode,currentTime,daysBeforeStart);

        assertFalse(valid);
    }


    @Test
    public void shouldReturTrueGyldighetsperiodeNotNullEndDateIsNull(){
        Date startDate = Date.from((LocalDate.of(2020,8,1)
                .atStartOfDay(ZoneId.of("Z")).toInstant()));
        Date sluttDate = null;
        Periode gyldighetsPeriode = new Periode();
        gyldighetsPeriode.setSlutt(sluttDate);
        gyldighetsPeriode.setStart(startDate);

        boolean valid = gyldighetsperiodeService.isValid(gyldighetsPeriode,currentTime,daysBeforeStart);

        assertTrue(valid);
    }

}