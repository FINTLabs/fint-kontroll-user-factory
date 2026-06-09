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
import no.fintlabs.links.ResourceLinkUtil;
import no.fintlabs.user.User;
import no.novari.kafka.consuming.ErrorHandlerConfiguration;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.consuming.ListenerConfiguration;
import no.novari.kafka.consuming.ParameterizedListenerContainerFactory;
import no.novari.kafka.consuming.ParameterizedListenerContainerFactoryService;
import no.novari.kafka.topic.name.EntityTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class EntityConsumersConfiguration {

    private final ParameterizedListenerContainerFactoryService listenerContainerFactoryService;
    private final ErrorHandlerFactory errorHandlerFactory;

    private static final ListenerConfiguration DEFAULT_LISTENER_CONFIGURATION = ListenerConfiguration
            .stepBuilder()
            .groupIdApplicationDefault()
            .maxPollRecordsKafkaDefault()
            .maxPollIntervalKafkaDefault()
            .continueFromPreviousOffsetOnAssignment()
            .build();

    private static EntityTopicNameParameters entityTopicNameParameters(String resourceName) {
        return EntityTopicNameParameters
                .builder()
                .topicNamePrefixParameters(TopicNamePrefixParameters
                        .stepBuilder()
                        .orgIdApplicationDefault()
                        .domainContextApplicationDefault()
                        .build())
                .resourceName(resourceName)
                .build();
    }

    private <T> ParameterizedListenerContainerFactory<T> createEntityRecordListenerContainerFactory(
            Class<T> resourceClass,
            Consumer<ConsumerRecord<String, T>> recordProcessor
    ) {
        return listenerContainerFactoryService.createRecordListenerContainerFactory(
                resourceClass,
                recordProcessor,
                DEFAULT_LISTENER_CONFIGURATION,
                errorHandlerFactory.createErrorHandler(ErrorHandlerConfiguration.<T>builder().build())
        );
    }

    private <T extends FintLinks> ConcurrentMessageListenerContainer<String, T> createCacheConsumer(
            String resourceReference,
            Class<T> resourceClass,
            FintCache<String, T> cache
    ) {
        return createEntityRecordListenerContainerFactory(
                resourceClass,
                consumerRecord -> cache.put(
                        ResourceLinkUtil.getSelfLinks(consumerRecord.value()),
                        consumerRecord.value()
                )
        ).createContainer(entityTopicNameParameters(resourceReference));
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonalressursResource> personalressursResourceEntityConsumer(
            FintCache<String, PersonalressursResource> personalressursResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon-personal-personalressurs",
                PersonalressursResource.class,
                personalressursResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonResource> personResourceEntityConsumer(
            FintCache<String, PersonResource> personResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon-personal-person",
                PersonResource.class,
                personResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, OrganisasjonselementResource> organisasjonselementResourceEntityConsumer(
            FintCache<String, OrganisasjonselementResource> organisasjonselementResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon-organisasjon-organisasjonselement",
                OrganisasjonselementResource.class,
                organisasjonselementResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ArbeidsforholdResource> arbeidsforholdResourceEntityConsumer(
            FintCache<String, ArbeidsforholdResource> arbeidsforholdResourceCache
    ) {
        return createCacheConsumer(
                "administrasjon-personal-arbeidsforhold",
                ArbeidsforholdResource.class,
                arbeidsforholdResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ElevResource> elevResourceEntityConsumer(
            FintCache<String, ElevResource> elevResourceCache
    ) {
        return createCacheConsumer(
                "utdanning-elev-elev",
                ElevResource.class,
                elevResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonResource> personResourceUtdanningEntityConsumer(
            FintCache<String, PersonResource> personResourceCache
    ) {
        return createCacheConsumer(
                "utdanning-elev-person",
                PersonResource.class,
                personResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ElevforholdResource> elevforholdResourceEntityConsumer(
            FintCache<String, ElevforholdResource> elevforholdResourceCache
    ) {
        return createCacheConsumer(
                "utdanning-elev-elevforhold",
                ElevforholdResource.class,
                elevforholdResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleResource> skoleResourceEntityConsumer(
            FintCache<String, SkoleResource> skoleResourceCache
    ) {
        return createCacheConsumer(
                "utdanning-utdanningsprogram-skole",
                SkoleResource.class,
                skoleResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleressursResource> skoleressursResourceEntityConsumer(
            FintCache<String, SkoleressursResource> skoleressursResourceCache,
            FintCache<String, Long> employeeInSchoolCache
    ) {
        ParameterizedListenerContainerFactory<SkoleressursResource> skoleressursConsumerFactory
                = createEntityRecordListenerContainerFactory(
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

        return skoleressursConsumerFactory.createContainer(entityTopicNameParameters("utdanning-elev-skoleressurs"));
    }

    @Bean
    ConcurrentMessageListenerContainer<String, EntraUserPayload> entraUserResourceEntityConsumer(
            FintCache<String, EntraUser> azureUserResourceCache,
            EntraUserService entraUserService) {
        ParameterizedListenerContainerFactory<EntraUserPayload> entraUserConsumerFactory
                = createEntityRecordListenerContainerFactory(
                EntraUserPayload.class,
                consumerRecord -> {
                    if (consumerRecord.value() == null) {
                        log.info("Received tombstone for user: {}", consumerRecord.key());
                        entraUserService.handleTombstoneForId(consumerRecord.key());
                    } else {
                        EntraUser entraUser = EntraUser.fromRecord(consumerRecord);
                        if (entraUser.getEmployeeOrStudentId() == null) {
                            log.warn("Skipping user with missing employeeId or studentId: {}", entraUser.userPrincipalName());
                            return;
                        }
                        azureUserResourceCache.put(
                                entraUser.getEmployeeOrStudentId(),
                                entraUser
                        );
                        log.debug("Saved to cache: " + entraUser.userPrincipalName());
                    }
                }
        );
        return entraUserConsumerFactory.createContainer(entityTopicNameParameters("graph-user"));

    }


    @Bean
    ConcurrentMessageListenerContainer<String, User> userResourceEntityConsumer(
            FintCache<String, User> publishUserCache
    ) {
        return createEntityRecordListenerContainerFactory(
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
        ).createContainer(entityTopicNameParameters("user"));
    }

}
