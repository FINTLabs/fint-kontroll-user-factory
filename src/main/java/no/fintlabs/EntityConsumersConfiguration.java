package no.fintlabs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.entraUser.EntraUserPayload;
import no.novari.fint.model.resource.FintLinks;
import no.novari.fint.model.resource.administrasjon.organisasjon.OrganisasjonselementResource;
import no.novari.fint.model.resource.administrasjon.personal.ArbeidsforholdResource;
import no.novari.fint.model.resource.administrasjon.personal.PersonalressursResource;
import no.novari.fint.model.resource.felles.PersonResource;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.novari.fint.model.resource.utdanning.elev.ElevforholdResource;
import no.novari.fint.model.resource.utdanning.elev.SkoleressursResource;
import no.novari.fint.model.resource.utdanning.utdanningsprogram.SkoleResource;
import no.fintlabs.cache.FintCache;
import no.fintlabs.entraUser.EntraUser;
import no.fintlabs.entraUser.EntraUserService;
import no.fintlabs.kafka.common.ListenerContainerFactory;
import no.fintlabs.kafka.entity.EntityConsumerFactoryService;
import no.fintlabs.kafka.entity.topic.EntityTopicNameParameters;
import no.fintlabs.kafka.entity.topic.EntityTopicNamePatternParameters;
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.user.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EntityConsumersConfiguration {

    private final EntityConsumerFactoryService entityConsumerFactoryService;

    private <T extends FintLinks> ConcurrentMessageListenerContainer<String, T> createCacheConsumer(
            String resourceReference,
            Class<T> resourceClass,
            FintCache<String, T> cache
    ) {
        return entityConsumerFactoryService.createFactory(
                resourceClass,
                consumerRecord -> cache.put(
                        ResourceLinkUtil.getSelfLinks(consumerRecord.value()),
                        consumerRecord.value()
                )
        ).createContainer(EntityTopicNameParameters.builder().resource(resourceReference).build());
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonalressursResource> personalressursResourceEntityConsumer(
            FintCache<String, PersonalressursResource> personalressursResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon.personal.personalressurs",
                PersonalressursResource.class,
                personalressursResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonResource> personResourceEntityConsumer(
            FintCache<String, PersonResource> personResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon.personal.person",
                PersonResource.class,
                personResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, OrganisasjonselementResource> organisasjonselementResourceEntityConsumer(
            FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon.organisasjon.organisasjonselement",
                OrganisasjonselementResource.class,
                organisasjonselementResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ArbeidsforholdResource> arbeidsforholdResourceEntityConsumer(
            FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon.personal.arbeidsforhold",
                ArbeidsforholdResource.class,
                arbeidsforholdResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ElevResource> elevResourceEntityConsumer(
            FintCache<String, ElevResource> elevResourceCache
    ) {
        return createCacheConsumer(
                "utdanning.elev.elev",
                ElevResource.class,
                elevResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonResource> personResourceUtdanningEntityConsumer(
            FintCache<String, PersonResource> personResourceCache
    ) {
        return createCacheConsumer(
                "utdanning.elev.person",
                PersonResource.class,
                personResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ElevforholdResource> elevforholdResourceEntityConsumer(
            FintCache<String, ElevforholdResource> elevforholdResourceCache
    ) {
        return createCacheConsumer(
                "utdanning.elev.elevforhold",
                ElevforholdResource.class,
                elevforholdResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleResource> skoleResourceEntityConsumer(
            FintCache<String, SkoleResource> skoleResourceCache
    ) {
        return createCacheConsumer(
                "utdanning.utdanningsprogram.skole",
                SkoleResource.class,
                skoleResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleressursResource> skoleressursResourceEntityConsumer(
            FintCache<String, SkoleressursResource> skoleressursResourceCache,
            FintCache<String, Long> employeeInSchoolCache
    ) {
        ListenerContainerFactory<SkoleressursResource, EntityTopicNameParameters, EntityTopicNamePatternParameters> skoleressursConsumerFactory
                = entityConsumerFactoryService.createFactory(
                SkoleressursResource.class,
                consumerRecord -> {
                    String personalressursHref = consumerRecord.value().getPersonalressurs().getFirst().getHref();
                    String key = personalressursHref.substring(personalressursHref.lastIndexOf("/") + 1);
                    skoleressursResourceCache.put(
                            key,
                            consumerRecord.value()
                    );
                    Long numberOfUndervisningsforhold = (long) consumerRecord.value().getUndervisningsforhold().size();
                    employeeInSchoolCache.put(
                            key,
                            numberOfUndervisningsforhold
                    );
                }
        );

        return skoleressursConsumerFactory.createContainer(EntityTopicNameParameters.builder().resource("utdanning-elev-skoleressurs").build());
    }

    @Bean
    ConcurrentMessageListenerContainer<String, EntraUserPayload> entraUserResourceEntityConsumer(
            FintCache<String, EntraUser> azureUserResourceCache,
            EntraUserService entraUserService) {
        ListenerContainerFactory<EntraUserPayload, EntityTopicNameParameters, EntityTopicNamePatternParameters> entraUserConsumerFactory
                = entityConsumerFactoryService.createFactory(
                EntraUserPayload.class,
                consumerRecord -> {
                    if (consumerRecord.value() == null) {
                        log.info("Received tombstone for user: {}", consumerRecord.key());
                        entraUserService.handleTombstoneForId(consumerRecord.key());
                    } else {
                        EntraUser entraUser = EntraUser.fromRecord(consumerRecord);

                        azureUserResourceCache.put(
                                entraUser.getEmployeeOrStudentId(),
                                entraUser
                        );
                        log.debug("Saved to cache: " + entraUser.userPrincipalName());
                    }
                }
        );
        return entraUserConsumerFactory.createContainer(EntityTopicNameParameters.builder().resource("graph-user").build());

    }


    @Bean
    ConcurrentMessageListenerContainer<String, User> userResourceEntityConsumer(
            FintCache<String, User> publishUserCache
    ) {
        return entityConsumerFactoryService.createFactory(
                User.class,
                consumerRecord -> {
                    if (consumerRecord.value() == null) {
                        if (consumerRecord.key() != null) {
                            publishUserCache.remove(consumerRecord.key());
                        }
                        return;
                    }

                    publishUserCache.put(
                            consumerRecord.value().getResourceId(),
                            consumerRecord.value()
                    );
                }
        ).createContainer(EntityTopicNameParameters.builder().resource("user").build());
    }

}
