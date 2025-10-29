package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;

import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.user.UserUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ElevforholdService {

    private final FintCache<String, ElevforholdResource> elevforholdResourceCache;
    private final FintCache<String, SkoleResource> skoleResourceCache;
    private final FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;

    public ElevforholdService(
            FintCache<String, ElevforholdResource> elevforholdResourceCache,
            FintCache<String, SkoleResource> skoleResourceCache,
            FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache) {
        this.elevforholdResourceCache = elevforholdResourceCache;
        this.skoleResourceCache = skoleResourceCache;
        this.organisasjonselementResourceCache = organisasjonselementResourceCache;
    }
    public Long getNumberOFElevforholdInCache() {
        return elevforholdResourceCache.getNumberOfDistinctValues();
    }

    public List<ElevforholdResource> getAllForElev(List<Link> elevResourceForholds) {
        return elevResourceForholds
                .stream()
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .map(elevforholdResourceCache::getOptional)
                .flatMap(Optional::stream)
                .filter(this::hasGyldighetsperiode)
                .toList();
    }
    public Optional<ElevforholdResource> getMainElevforhold(
            List<ElevforholdResource> elevforholds,
            Date currentTime
    ) {

        Optional<ElevforholdResource> activeNow = elevforholds.stream()
                .filter(forhold -> UserUtils.isPeriodActive(forhold.getGyldighetsperiode(), currentTime))
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
        String skoleHref = elevforhold.getSkole().getFirst().getHref().toLowerCase();
        return skoleResourceCache.getOptional(skoleHref);


    }

    public Optional<OrganisasjonselementResource> getSkoleOrgUnit(SkoleResource skoleResource) {
        // legg inn sjekk på null
        String skoleOrgUnitref = skoleResource.getOrganisasjon().getFirst().getHref();
        //Legg på sjekk på null
        OrganisasjonselementResource organisasjonselementResource = organisasjonselementResourceCache
                .get(ResourceLinkUtil.organisasjonsKodeToLowerCase(skoleOrgUnitref));
        return Optional.ofNullable(organisasjonselementResource);
    }

}
