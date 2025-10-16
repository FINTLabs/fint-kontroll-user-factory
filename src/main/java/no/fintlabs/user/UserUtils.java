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
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class UserUtils {

    private static int DAYS_BEFORE_START_EMPLOYEE;
    private static int DAYS_BEFORE_START_STUDENT;

    private static GyldighetsperiodeService gyldighetsperiodeService = new GyldighetsperiodeService();
    private static FintCache<String, User> publishUserCache;

    public UserUtils(
            GyldighetsperiodeService gyldighetsperiodeService,
            FintCache<String, User> publishUserCache) {
        UserUtils.gyldighetsperiodeService = gyldighetsperiodeService;
        UserUtils.publishUserCache = publishUserCache;

    }

    public static int getDaysBeforeStartStudent() {
        return DAYS_BEFORE_START_STUDENT;
    }

    @Value("${fint.kontroll.user.days-before-start-student}")
    private void setDaysBeforeStartStudent(int daysBeforeStartStudent) {
        DAYS_BEFORE_START_STUDENT = daysBeforeStartStudent;
    }
    @Value("${fint.kontroll.user.days-before-start-employee}")
    private void setDaysBeforeStartEmployee(int daysBeforeStartEmployee) {
        DAYS_BEFORE_START_EMPLOYEE = daysBeforeStartEmployee;
    }

    public static int getDaysBeforeStartEmployee() {
        return DAYS_BEFORE_START_EMPLOYEE;
    }

    public static String getFINTAnsattStatus(PersonalressursResource personalressursResource, Date currentTime) {
        Periode gyldighetsPeriode = personalressursResource.getAnsettelsesperiode();
        String status = gyldighetsperiodeService.isValid(gyldighetsPeriode, currentTime, getDaysBeforeStartEmployee())
                ? UserStatus.ACTIVE
                : UserStatus.DISABLED;

        if (UserStatus.DISABLED.equals(status)) {
            log.info("User is DISABLED from FINT. FINT ansattnummer: {}", personalressursResource.getAnsattnummer().getIdentifikatorverdi());
        }

        return status;
    }

    public static String getFINTElevStatus(ElevforholdResource elevforhold, Date currentTime) {
        String resoursID = elevforhold.getSystemId().getIdentifikatorverdi();
        Periode gyldighetsperiode = elevforhold.getGyldighetsperiode();
        String status = gyldighetsperiodeService.isValid(gyldighetsperiode, currentTime, getDaysBeforeStartStudent())
                ? UserStatus.ACTIVE
                : UserStatus.DISABLED;

        log.debug("Systemid: {} stop: {} Status: {}", resoursID, elevforhold.getGyldighetsperiode().getSlutt(), status);

        return status;
    }


    public static boolean isValidUserOnKafka(String resourceId) {
        return publishUserCache
                .getOptional(resourceId)
                .map(user -> !UserStatus.INVALID.equals(user.getStatus()))
                .orElse(false);
    }

    //TODO: Skrive om slik at den logger om bruker er på kafka eller ikke
    public static User getUserFromKafka(String resourceId) {
        Optional<User> userFromKafka = publishUserCache.getOptional(resourceId);
        log.info("Get user information from Kafka: {}", userFromKafka);

        return userFromKafka.orElse(null);
    }

    @Value("${fint.kontroll.user.days-before-start-employee}")
    private void daysBeforeStartEmployee(int daysBeforeStartEmployee) {
        DAYS_BEFORE_START_EMPLOYEE = daysBeforeStartEmployee;
    }


    public enum UserType {
        EMPLOYEESTAFF,
        EMPLOYEEFACULTY,
        STUDENT,
        AFFILIATE
    }

    public static Optional<User> createInvalidUser(String resourceId) {
        return Optional.of(User.builder().resourceId(resourceId).status(UserStatus.INVALID).build());
    }

}

