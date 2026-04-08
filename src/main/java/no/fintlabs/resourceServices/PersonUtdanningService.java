package no.fintlabs.resourceServices;

import no.fint.model.resource.felles.PersonResource;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PersonUtdanningService {
    private final FintCache<String,PersonResource> personResourceCache;

    public PersonUtdanningService(FintCache<String, PersonResource> personResourceCache) {
        this.personResourceCache = personResourceCache;
    }


    public Optional<PersonResource> getPersonUtdanning(ElevResource elevResource){
        return personResourceCache.getOptional(
                ResourceLinkUtil.getFirstLink(
                        elevResource::getPerson,
                        elevResource,
                        "Elev"
                )
        );

    }
}
