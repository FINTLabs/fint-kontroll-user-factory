package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.felles.kompleksedatatyper.Periode;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.resourceServices.GyldighetsperiodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

@Component
@Slf4j
public class UserUtils {

    private static int DAYS_BEFORE_START_EMPLOYEE;
    @Value("${fint.kontroll.user.days-before-start-employee}")
    private void daysBeforeStartEmployee(int daysBeforeStartEmployee) {
        UserUtils.DAYS_BEFORE_START_EMPLOYEE = daysBeforeStartEmployee;
    };

    private static int DAYS_BEFORE_START_STUDENT;
    @Value("${fint.kontroll.user.days-before-start-student}")
    private void setDaysBeforeStartStudent(int daysBeforeStartStudent) {
        UserUtils.DAYS_BEFORE_START_STUDENT = daysBeforeStartStudent;
    };


    private static GyldighetsperiodeService gyldighetsperiodeService = new GyldighetsperiodeService();
    private static FintCache<String,User> publishUserCache;


    public UserUtils(
            GyldighetsperiodeService gyldighetsperiodeService,
            FintCache<String,User> publishUserCache) {
        UserUtils.gyldighetsperiodeService = gyldighetsperiodeService;
        UserUtils.publishUserCache = publishUserCache;

    }

    public static String getFINTAnsattStatus(PersonalressursResource personalressursResource, Date currentTime) {
        Periode gyldighetsPeriode = personalressursResource.getAnsettelsesperiode();
        String status = gyldighetsperiodeService.isValid(gyldighetsPeriode,currentTime, DAYS_BEFORE_START_EMPLOYEE)
                ? UserStatus.ACTIVE.toString()
                :UserStatus.INACTIVE.toString();

        if (status.equals(UserStatus.INACTIVE.toString())){
            log.info("User is INACTIVE from FINT. FINT ansattnummer: {}", personalressursResource.getAnsattnummer().getIdentifikatorverdi());
        }

        return status;
    }

    public static String getFINTElevStatus(ElevforholdResource elevforhold, Date currentTime) {
        Periode gyldighetsperiode = elevforhold.getGyldighetsperiode();
        String status = gyldighetsperiodeService.isValid(gyldighetsperiode,currentTime, DAYS_BEFORE_START_STUDENT)
                ? UserStatus.ACTIVE.toString()
                :UserStatus.INACTIVE.toString();

        if (status.equals(UserStatus.INACTIVE.toString())){
            log.info("Elevforhold is INACTIVE from FINT: {}", elevforhold.getSystemId().getIdentifikatorverdi());
        }
        return status;
    }

    public static boolean isUserAlreadyOnKafka(String resourceId){
        Optional<User> userhash = publishUserCache.getOptional(resourceId);

        return userhash.isPresent();
    }

    //TODO: Skrive om slik at den logger om bruker er på kafka eller ikke
    public static User getUserFromKafka(String resourceId){
        Optional<User> userFromKafka = publishUserCache.getOptional(resourceId);
        log.info("Get user information from Kafka: {}", userFromKafka);

        return userFromKafka.orElse(null);
    }


    public enum UserType {
        EMPLOYEESTAFF,
        EMPLOYEEFACULTY,
        STUDENT,
        AFFILIATE
    }

    public enum UserStatus {
        ACTIVE,
        INACTIVE
    }


}

