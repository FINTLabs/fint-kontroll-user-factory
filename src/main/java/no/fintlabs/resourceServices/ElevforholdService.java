package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;

import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.novari.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ElevforholdService {

    @Value("${fint.kontroll.user.days-before-start-student:0}")
    private int daysBeforeStartStudent;

    private final GyldighetsperiodeService gyldighetsperiodeService;
    private final FintCache<String, ElevforholdResource> elevforholdResourceCache;
    private final FintCache<String, SkoleResource> skoleResourceCache;
    private final FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;

    public ElevforholdService(
            GyldighetsperiodeService gyldighetsperiodeService,
            FintCache<String, ElevforholdResource> elevforholdResourceCache,
            FintCache<String, SkoleResource> skoleResourceCache,
            FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache) {
        this.gyldighetsperiodeService = gyldighetsperiodeService;
        this.elevforholdResourceCache = elevforholdResourceCache;
        this.skoleResourceCache = skoleResourceCache;
        this.organisasjonselementResourceCache = organisasjonselementResourceCache;
    }
    public Long getNumberOfElevforholdInCache() {
        return elevforholdResourceCache.getNumberOfDistinctValues();
    }

    public List<ElevforholdResource> getAllForElev(List<Link> elevResourceForholds) {
        if (elevResourceForholds == null || elevResourceForholds.isEmpty()) {
            return List.of();
        }

        return elevResourceForholds
                .stream()
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .map(elevforholdResourceCache::getOptional)
                .flatMap(Optional::stream)
                .filter(this::hasGyldighetsperiode)
                .toList();
    }

    public ResourceLookupResult<List<ElevforholdResource>> resolveAllForElev(List<Link> elevResourceForholds) {
        if (elevResourceForholds == null || elevResourceForholds.isEmpty()) {
            return ResourceLookupResult.missingLink();
        }

        List<ElevforholdResource> elevforholdResources = new ArrayList<>();
        for (Link link : elevResourceForholds) {
            if (link == null || link.getHref() == null || link.getHref().isBlank()) {
                return ResourceLookupResult.missingLink();
            }

            String href = ResourceLinkUtil.systemIdToLowerCase(link.getHref());
            Optional<ElevforholdResource> elevforholdResource = elevforholdResourceCache.getOptional(href);
            if (elevforholdResource.isEmpty()) {
                log.info("Elevforhold not found in cache: {}", href);
                return ResourceLookupResult.cacheMiss();
            }

            elevforholdResources.add(elevforholdResource.get());
        }

        return ResourceLookupResult.found(elevforholdResources
                .stream()
                .filter(this::hasGyldighetsperiode)
                .toList());
    }

    public Optional<ElevforholdResource> getMainElevforhold(
            List<ElevforholdResource> elevforholds,
            Date currentTime
    ) {

        Optional<ElevforholdResource> activeNow = elevforholds.stream()
                .filter(forhold -> gyldighetsperiodeService.isValid(
                        forhold.getGyldighetsperiode(), currentTime, daysBeforeStartStudent
                ))
                .findFirst();
        if (activeNow.isPresent()) {
            return activeNow;
        }

        return elevforholds.stream().findFirst();
    }

    private boolean hasGyldighetsperiode(ElevforholdResource elevforholdResource) {
        return elevforholdResource.getGyldighetsperiode() != null;
    }


    public Optional<SkoleResource> getSkole(ElevforholdResource elevforhold) {
        return resolveSkole(elevforhold).asOptional();
    }

    public ResourceLookupResult<SkoleResource> resolveSkole(ElevforholdResource elevforhold) {
        Optional<String> skoleHref = getFirstHref(elevforhold.getSkole()).map(String::toLowerCase);
        return skoleHref.map(s -> skoleResourceCache.getOptional(s)
                .map(ResourceLookupResult::found)
                .orElseGet(() -> {
                    log.info("Skole not found in cache: {}", s);
                    return ResourceLookupResult.cacheMiss();
                })).orElseGet(ResourceLookupResult::missingLink);

    }

    public Optional<OrganisasjonselementResource> getSkoleOrgUnit(SkoleResource skoleResource) {
        return resolveSkoleOrgUnit(skoleResource).asOptional();
    }

    public ResourceLookupResult<OrganisasjonselementResource> resolveSkoleOrgUnit(SkoleResource skoleResource) {
        if (skoleResource == null) {
            log.info("SkoleResource does not have organisasjon");
            return ResourceLookupResult.missingLink();
        }

        Optional<String> skoleOrgUnitHref = getFirstHref(skoleResource.getOrganisasjon());
        if (skoleOrgUnitHref.isEmpty()) {
            log.info("SkoleResource does not have organisasjon");
            return ResourceLookupResult.missingLink();
        }

        String skoleOrgUnitref = skoleOrgUnitHref.get();
        OrganisasjonselementResource organisasjonselementResource = organisasjonselementResourceCache
                .get(ResourceLinkUtil.organisasjonsKodeToLowerCase(skoleOrgUnitref));
        if (organisasjonselementResource == null) {
            log.info("Organisasjonselement not found in cache: {}", skoleOrgUnitref);
            return ResourceLookupResult.cacheMiss();
        }

        return ResourceLookupResult.found(organisasjonselementResource);
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
