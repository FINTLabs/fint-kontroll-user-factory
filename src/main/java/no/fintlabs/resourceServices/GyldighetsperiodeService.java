package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Periode;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;

@Service
@Slf4j
public class GyldighetsperiodeService {

    public boolean isValid(Periode gyldighetsperiode, Date currentTime, int days) {
        if (gyldighetsperiode == null) {
            throw new NullPeriodeException();
        }
        Date startInit = gyldighetsperiode.getStart();
        Date start = getStartDate(startInit, days);
        return currentTime.after(start)
                && isEndValid(gyldighetsperiode.getSlutt(), currentTime);
    }

    private boolean isEndValid(Date end, Date currentTime) {
        return end == null || currentTime.before(end);
    }

    public Date getStartDate(Date date, int daysBeforeStart) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, -daysBeforeStart);
        return calendar.getTime();
    }

    public static class NullPeriodeException extends RuntimeException {
    }

}
