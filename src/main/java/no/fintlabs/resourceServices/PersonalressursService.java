package no.fintlabs.resourceServices;

import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class PersonalressursService {

    private final GyldighetsperiodeService gyldighetsperiodeService;
    private final FintCache<String, PersonalressursResource> personalressursResourceCache;


    public PersonalressursService(
            GyldighetsperiodeService gyldighetsperiodeService,
            FintCache<String, PersonalressursResource> personalressursResourceCache
    ) {
        this.gyldighetsperiodeService = gyldighetsperiodeService;
        this.personalressursResourceCache = personalressursResourceCache;
    }

    public List<PersonalressursResource> getAllValid(Date currentTime) {
        return personalressursResourceCache.getAllDistinct()
                .stream()
                .filter(personalressursResource -> gyldighetsperiodeService.isValid(
                        personalressursResource.getAnsettelsesperiode(),
                        currentTime
                ))
                .filter(personalressursResource -> !personalressursResource.getArbeidsforhold().isEmpty())
                .toList();
    }

    public List<PersonalressursResource> getAllUsersfromCache(){
        return personalressursResourceCache.getAllDistinct()
                .stream()
                .filter(personalressursResource -> !personalressursResource.getArbeidsforhold().isEmpty())
                .toList();
    }

    public boolean isPersonalressursValid(PersonalressursResource personalressursResource, Date currentTime) {
        return gyldighetsperiodeService.isValid(personalressursResource.getAnsettelsesperiode(), currentTime);
    }

    public String getResourceId(PersonalressursResource personalressursResource) {
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(personalressursResource);
        String resourceId = hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") +1);

        return resourceId;
    }

}
