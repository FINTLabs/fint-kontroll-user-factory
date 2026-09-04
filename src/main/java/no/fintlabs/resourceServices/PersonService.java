package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.novari.fint.model.resource.felles.PersonResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PersonService {

    private final FintCache<String, PersonResource> personResourceCache;

    public PersonService(FintCache<String, PersonResource> personResourceCache) {
        this.personResourceCache = personResourceCache;
    }

    public Optional<PersonResource> getPerson(PersonalressursResource personalressursResource) {
        return resolvePerson(personalressursResource).asOptional();
    }

    public ResourceLookupResult<PersonResource> resolvePerson(PersonalressursResource personalressursResource) {
        if (personalressursResource == null) {
            return ResourceLookupResult.missingLink();
        }

        Optional<String> personHref = getFirstHref(personalressursResource.getPerson())
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
