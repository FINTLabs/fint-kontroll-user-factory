package no.fintlabs.resourceServices;

import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PersonalressursService {

    private final FintCache<String, PersonalressursResource> personalressursResourceCache;


    public PersonalressursService(
            FintCache<String, PersonalressursResource> personalressursResourceCache
    ) {
        this.personalressursResourceCache = personalressursResourceCache;
    }

    public List<PersonalressursResource> getAllUsersfromCache() {
        return personalressursResourceCache.getAllDistinct()
                .stream()
                .filter(personalressursResource -> !personalressursResource.getArbeidsforhold().isEmpty())
                .toList();
    }

    public String getResourceId(PersonalressursResource personalressursResource) {
        String hrefSelfLink = ResourceLinkUtil.getFirstSelfLink(personalressursResource);

        return hrefSelfLink.substring(hrefSelfLink.lastIndexOf("/") + 1);
    }

}
