package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.Link;
import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.links.ResourceLinkUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ArbeidsforholdService {

    @Value("${fint.kontroll.user.days-before-start-employee:0}")
    private int daysBeforeStartEmployee;

    private final GyldighetsperiodeService gyldighetsperiodeService;
    private final FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache;
    private final FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache;

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
            Collection<Link> arbeidsforholdLinks,
            Date currentTime,
            String resourceId) {
        List<ArbeidsforholdResource> allValidArbeidsresurser = getAllValidArbeidsforholdAsList(arbeidsforholdLinks,
                currentTime, resourceId);
        Optional<ArbeidsforholdResource> hovedarbeidsforhold =  getValidMainArbeidsforhold(allValidArbeidsresurser, currentTime, resourceId)
                .or(() -> getValidNonMainArbeidsforhold(allValidArbeidsresurser, currentTime,resourceId));
        hovedarbeidsforhold.ifPresent(arbeidsforholdResource -> log.info("Hovedarbeidsforhold funnet:  {} for user with resourceId {}", arbeidsforholdResource.getSystemId().getIdentifikatorverdi(), resourceId));
        if (hovedarbeidsforhold.isEmpty()){
            log.info("No hovedarbeidsforhold found for resourceId {}", resourceId);
        }

        return hovedarbeidsforhold;
    }


    public List<ArbeidsforholdResource> getAllValidArbeidsforholdAsList(
            Collection<Link> arbeidsforholdLinks,
            Date currenTime,
            String resourceId) {

        return arbeidsforholdLinks
                .stream()
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .map(arbeidsforholdResourceCache::getOptional)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(arbeidsforholdResource -> isValid(arbeidsforholdResource, currenTime))
                .toList();
    }


    private Optional<ArbeidsforholdResource> getValidMainArbeidsforhold(
            List<ArbeidsforholdResource> arbeidsforholdResources, Date currentTime, String resourceId) {

        return arbeidsforholdResources
                .stream()
                .filter(ArbeidsforholdResource::getHovedstilling)
                .filter(arbeidsforholdResource -> isValid(arbeidsforholdResource, currentTime))
                .findFirst();
    }

    private Optional<ArbeidsforholdResource> getValidNonMainArbeidsforhold(
            List<ArbeidsforholdResource> arbeidsforholdResources, Date currentTime, String resourceId) {

        return arbeidsforholdResources
                .stream()
                .filter(arbeidsforholdResource -> !arbeidsforholdResource.getHovedstilling())
                .filter(arbeidsforholdResource -> isValid(arbeidsforholdResource, currentTime))
                .max(Comparator.comparingLong(ArbeidsforholdResource::getAnsettelsesprosent));
    }


    private boolean isValid(ArbeidsforholdResource arbeidsforholdResource, Date currentTime) {
        return gyldighetsperiodeService.isValid(
                arbeidsforholdResource.getGyldighetsperiode(), currentTime, daysBeforeStartEmployee
        );
    }


    public List<Optional<OrganisasjonselementResource>> getAllArbeidssteder(List<ArbeidsforholdResource> arbeidsforholdResourceList,
                                                                            Date currentTime) {

        return arbeidsforholdResourceList
                .stream()
                .map(arbeidsforholdResource -> getArbeidssted(arbeidsforholdResource, currentTime))
                .toList();
    }


    public Optional<OrganisasjonselementResource> getArbeidssted(ArbeidsforholdResource arbeidsforholdResource, Date currentTime) {


        return ResourceLinkUtil.getOptionalFirstLink(arbeidsforholdResource::getArbeidssted)
                .map(ResourceLinkUtil::organisasjonsIdToLowerCase)
                .flatMap(organisasjonselementResourceCache::getOptional)
                .filter(organisasjonselementResource ->
                        gyldighetsperiodeService.isValid(organisasjonselementResource.getGyldighetsperiode(), currentTime,daysBeforeStartEmployee));
    }


}
