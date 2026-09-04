package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.novari.fint.model.resource.felles.PersonResource;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import no.novari.fint.model.resource.Link;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PersonUtdanningService {
    private final FintCache<String,PersonResource> personResourceCache;

    public PersonUtdanningService(FintCache<String, PersonResource> personResourceCache) {
        this.personResourceCache = personResourceCache;
    }


    public Optional<PersonResource> getPersonUtdanning(ElevResource elevResource){
        return resolvePersonUtdanning(elevResource).asOptional();
    }

    public ResourceLookupResult<PersonResource> resolvePersonUtdanning(ElevResource elevResource) {
        if (elevResource == null) {
            return ResourceLookupResult.missingLink();
        }

        Optional<String> personHref = getFirstHref(elevResource.getPerson())
                .map(ResourceLinkUtil::systemIdToLowerCase);
        if (personHref.isEmpty()) {
            return ResourceLookupResult.missingLink();
        }

        return personResourceCache.getOptional(personHref.get())
                .map(ResourceLookupResult::found)
                .orElseGet(() -> {
                    log.info("Person not found in cache: {}", personHref.get());
                    return ResourceLookupResult.cacheMiss();
                });
    }

    private Optional<String> getFirstHref(List<Link> links) {
        return Optional.ofNullable(links)
                .flatMap(linkList -> linkList
                        .stream()
                        .filter(link -> link != null && link.getHref() != null && !link.getHref().isBlank())
                        .findFirst())
                .map(Link::getHref);
    }
}
