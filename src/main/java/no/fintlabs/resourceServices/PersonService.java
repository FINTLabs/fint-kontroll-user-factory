package no.fintlabs.resourceServices;

import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.felles.PersonResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PersonService {

    private final FintCache<String, PersonResource> personResourceCache;

    public PersonService(FintCache<String, PersonResource> personResourceCache) {
        this.personResourceCache = personResourceCache;
    }

    public Optional<PersonResource> getPerson(PersonalressursResource personalressursResource) {
        return personResourceCache.getOptional(
                ResourceLinkUtil.getFirstLink(
                        personalressursResource::getPerson,
                        personalressursResource,
                        "Person"
                ));
    }

}
