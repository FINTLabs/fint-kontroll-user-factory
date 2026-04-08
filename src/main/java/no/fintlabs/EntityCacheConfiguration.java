package no.fintlabs;

import no.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.fint.model.resource.felles.PersonResource;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.fint.model.resource.utdanning.elev.SkoleressursResource;
import no.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.azureUser.AzureUser;
import no.fintlabs.cache.FintCache;
import no.fintlabs.cache.FintCacheManager;
import no.fintlabs.user.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class EntityCacheConfiguration {

    private final FintCacheManager fintCacheManager;

    public EntityCacheConfiguration(FintCacheManager fintCacheManager) {
        this.fintCacheManager = fintCacheManager;
    }

    @Bean
    FintCache<String, PersonalressursResource> personalressursResourceCache() {
        return createCache(PersonalressursResource.class);
    }

    @Bean
    FintCache<String, PersonResource> personResourceCache() {
        return createCache(PersonResource.class);
    }

    @Bean
    FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache() {
        return createCache(OrganisasjonselementResource.class);
    }

    @Bean
    FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache() {
        return createCache(ArbeidsforholdResource.class);
    }

    @Bean
    FintCache<String, ElevResource> elevResourceCache() {
        return createCache(ElevResource.class);
    }

    @Bean
    FintCache<String, ElevforholdResource> elevforholdResourceCache(){
        return createCache(ElevforholdResource.class);
    }

    @Bean
    FintCache<String, SkoleResource> skoleResourceCache() {return createCache(SkoleResource.class);}

    @Bean
    FintCache<String, SkoleressursResource> skoleressursResourceCache() {return createCache(SkoleressursResource.class);}

    @Bean
    FintCache<String, AzureUser> azureUserResourceCache(){return createCache(AzureUser.class);}

    @Bean
    FintCache<String, Long> employeeInSchoolCache() {return createCache(Long.class);}


    @Bean
    FintCache<String, User> publishUserCache() { return createCache(User.class);}


    private <V> FintCache<String, V> createCache(Class<V> resourceClass) {
        return fintCacheManager.createCache(
                resourceClass.getName().toLowerCase(Locale.ROOT),
                String.class,
                resourceClass
        );
    }






}
