package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import no.novari.fint.model.resource.Link;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ArbeidsforholdService {

    private final GyldighetsperiodeService gyldighetsperiodeService;
    private final FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache;
    private final FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;
    @Value("${fint.kontroll.user.days-before-start-employee:0}")
    private int daysBeforeStartEmployee;

    public ArbeidsforholdService(
            GyldighetsperiodeService gyldighetsperiodeService,
            FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache,
            FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache
    ) {
        this.gyldighetsperiodeService = gyldighetsperiodeService;
        this.arbeidsforholdResourceCache = arbeidsforholdResourceCache;
        this.organisasjonselementResourceCache = organisasjonselementResourceCache;
    }

    public Optional<ArbeidsforholdResource> getHovedArbeidsforhold(
            List<ArbeidsforholdResource> arbeidsforholdResources,
            Date currentTime,
            String resourceId) {

        List<ArbeidsforholdResource> allValidArbeidsforhold =
                getAllValidArbeidsforholdAsList(arbeidsforholdResources, currentTime);
        return selectHovedArbeidsforhold(allValidArbeidsforhold, resourceId);
    }

    private Optional<ArbeidsforholdResource> selectHovedArbeidsforhold(
            List<ArbeidsforholdResource> allValidArbeidsforhold,
            String resourceId) {

        Optional<ArbeidsforholdResource> hovedarbeidsforhold = getMainArbeidsforhold(allValidArbeidsforhold)
                .or(() -> getNonMainArbeidsforhold(allValidArbeidsforhold));
        hovedarbeidsforhold.ifPresent(arbeidsforholdResource -> log.info("Hovedarbeidsforhold funnet:  {} for user with resourceId {}", arbeidsforholdResource.getSystemId().getIdentifikatorverdi(), resourceId));
        if (hovedarbeidsforhold.isEmpty()) {
            log.info("No hovedarbeidsforhold found for resourceId {}", resourceId);
        }

        return hovedarbeidsforhold;
    }

    public ResourceLookupResult<List<ArbeidsforholdResource>> resolveAllArbeidsforhold(
            Collection<Link> arbeidsforholdLinks) {

        if (arbeidsforholdLinks == null || arbeidsforholdLinks.isEmpty()) {
            return ResourceLookupResult.missingLink();
        }

        List<ArbeidsforholdResource> arbeidsforholdResources = new ArrayList<>();
        for (Link link : arbeidsforholdLinks) {
            if (link == null || link.getHref() == null || link.getHref().isBlank()) {
                return ResourceLookupResult.missingLink();
            }

            String href = ResourceLinkUtil.systemIdToLowerCase(link.getHref());
            Optional<ArbeidsforholdResource> arbeidsforholdResource = arbeidsforholdResourceCache.getOptional(href);
            if (arbeidsforholdResource.isEmpty()) {
                log.info("Arbeidsforhold not found in cache: {}", href);
                return ResourceLookupResult.cacheMiss();
            }

            arbeidsforholdResources.add(arbeidsforholdResource.get());
        }

        return ResourceLookupResult.found(arbeidsforholdResources);
    }

    public List<ArbeidsforholdResource> getAllValidArbeidsforholdAsList(
            List<ArbeidsforholdResource> arbeidsforholdResources,
            Date currentTime) {

        return arbeidsforholdResources
                .stream()
                .filter(arbeidsforholdResource -> isValid(arbeidsforholdResource, currentTime))
                .toList();
    }

    private Optional<ArbeidsforholdResource> getMainArbeidsforhold(
            List<ArbeidsforholdResource> arbeidsforholdResources) {

        return arbeidsforholdResources
                .stream()
                .filter(arbeidsforholdResource -> Boolean.TRUE.equals(arbeidsforholdResource.getHovedstilling()))
                .findFirst();
    }

    private Optional<ArbeidsforholdResource> getNonMainArbeidsforhold(
            List<ArbeidsforholdResource> arbeidsforholdResources) {

        return arbeidsforholdResources
                .stream()
                .filter(arbeidsforholdResource -> !Boolean.TRUE.equals(arbeidsforholdResource.getHovedstilling()))
                .max(Comparator.comparingLong(this::getAnsettelsesprosent));
    }


    private long getAnsettelsesprosent(ArbeidsforholdResource arbeidsforholdResource) {
        Long ansettelsesprosent = arbeidsforholdResource.getAnsettelsesprosent();
        return ansettelsesprosent == null ? 0L : ansettelsesprosent;
    }

    private boolean isValid(ArbeidsforholdResource arbeidsforholdResource, Date currentTime) {
        return gyldighetsperiodeService.isValid(
                arbeidsforholdResource.getGyldighetsperiode(), currentTime, daysBeforeStartEmployee
        );
    }


    public List<Optional<OrganisasjonselementResource>> getAllArbeidssteder(
            List<ArbeidsforholdResource> arbeidsforholdResourceList,
            Date currentTime) {

        return arbeidsforholdResourceList
                .stream()
                .map(arbeidsforholdResource -> getArbeidssted(arbeidsforholdResource, currentTime))
                .toList();
    }


    public Optional<OrganisasjonselementResource> getArbeidssted(
            ArbeidsforholdResource arbeidsforholdResource,
            Date currentTime) {
        return resolveArbeidssted(arbeidsforholdResource, currentTime).asOptional();
    }

    public ResourceLookupResult<OrganisasjonselementResource> resolveArbeidssted(
            ArbeidsforholdResource arbeidsforholdResource,
            Date currentTime
    ) {
        if (arbeidsforholdResource == null) {
            return ResourceLookupResult.missingLink();
        }

        Optional<String> arbeidsstedHref = getFirstHref(arbeidsforholdResource.getArbeidssted())
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .map(ResourceLinkUtil::organisasjonsIdToLowerCase);

        if (arbeidsstedHref.isEmpty()) {
            return ResourceLookupResult.missingLink();
        }

        Optional<OrganisasjonselementResource> arbeidssted =
                organisasjonselementResourceCache.getOptional(arbeidsstedHref.get());
        if (arbeidssted.isEmpty()) {
            log.info("Arbeidssted not found in cache: {}", arbeidsstedHref.get());
            return ResourceLookupResult.cacheMiss();
        }

        if (!gyldighetsperiodeService.isValid(
                arbeidssted.get().getGyldighetsperiode(),
                currentTime,
                daysBeforeStartEmployee)) {
            log.info("Arbeidssted is not valid in selection window: {}", arbeidsstedHref.get());
            return ResourceLookupResult.notValid();
        }

        return ResourceLookupResult.found(arbeidssted.get());
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
