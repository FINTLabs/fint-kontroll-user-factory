package no.fintlabs;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.FintLinks;
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

@Configuration
@Slf4j
public class EntityConsumersConfiguration {

    private final ParameterizedListenerContainerFactoryService listenerContainerFactoryService;
    private final ErrorHandlerFactory errorHandlerFactory;

    public EntityConsumersConfiguration(
            ParameterizedListenerContainerFactoryService listenerContainerFactoryService,
            ErrorHandlerFactory errorHandlerFactory
    ) {
        this.listenerContainerFactoryService = listenerContainerFactoryService;
        this.errorHandlerFactory = errorHandlerFactory;
    }

    private <T extends FintLinks> ConcurrentMessageListenerContainer<String, T> createCacheConsumer(
            String resourceReference,
            Class<T> resourceClass,
            FintCache<String, T> cache
    ) {
        return createRecordListenerFactory(
                resourceClass,
                consumerRecord -> cache.put(
                        ResourceLinkUtil.getSelfLinks(consumerRecord.value()),
                        consumerRecord.value()
                )
        ).createContainer(topic(resourceReference));
    }

    private <T> ParameterizedListenerContainerFactory<T> createRecordListenerFactory(
            Class<T> resourceClass,
            java.util.function.Consumer<ConsumerRecord<String, T>> recordProcessor
    ) {
        return listenerContainerFactoryService.createRecordListenerContainerFactory(
                resourceClass,
                recordProcessor,
                ListenerConfiguration.stepBuilder()
                        .groupIdApplicationDefault()
                        .maxPollRecordsKafkaDefault()
                        .maxPollIntervalKafkaDefault()
                        .seekToBeginningOnAssignment()
                        .build(),
                errorHandlerFactory.createErrorHandler(
                        ErrorHandlerConfiguration.<T>stepBuilder()
                                .noRetries()
                                .skipFailedRecords()
                                .build()
                )
        );
    }

    private EntityTopicNameParameters topic(String resourceName) {
        return EntityTopicNameParameters.builder()
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters.stepBuilder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .resourceName(resourceName)
                .build();
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
            FintCache<String,ElevResource> elevResourceCache
    ){
        return createCacheConsumer(
                "utdanning.elev.elev",
                ElevResource.class,
                elevResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, PersonResource> personResourceUtdanningEntityConsumer(
            FintCache<String, PersonResource> personResourceCache
    ){
        return createCacheConsumer(
                "utdanning.elev.person",
                PersonResource.class,
                personResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, ElevforholdResource> elevforholdResourceEntityConsumer(
            FintCache<String, ElevforholdResource> elevforholdResourceCache
    ){
        return createCacheConsumer(
                "utdanning.elev.elevforhold",
                ElevforholdResource.class,
                elevforholdResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleResource> skoleResourceEntityConsumer(
            FintCache<String,SkoleResource> skoleResourceCache
    ){
        return createCacheConsumer(
                "utdanning.utdanningsprogram.skole",
                SkoleResource.class,
                skoleResourceCache
        );
    }

    @Bean
    ConcurrentMessageListenerContainer<String, SkoleressursResource> skoleressursResourceEntityConsumer(
            FintCache<String, SkoleressursResource> skoleressursResourceCache,
            FintCache<String,Long> employeeInSchoolCache
    ){
        ParameterizedListenerContainerFactory<SkoleressursResource> skoleressursConsumerFactory =
                createRecordListenerFactory(
                        SkoleressursResource.class,
                        consumerRecord -> {
                            String personalressursHref = consumerRecord.value().getPersonalressurs().get(0).getHref();
                            String key = personalressursHref.substring(personalressursHref.lastIndexOf("/") +1);
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

        return skoleressursConsumerFactory.createContainer(topic("utdanning-elev-skoleressurs"));
    }

    @Bean
    ConcurrentMessageListenerContainer<String,AzureUser> azureUserResourceEntityConsumer(
            FintCache<String, AzureUser> azureUserResourceCache
    ){
        return createRecordListenerFactory(
                AzureUser.class,
                consumerRecord -> {
                    AzureUser azureUser = consumerRecord.value();
                    log.debug("Trying to save: {}", azureUser.getUserPrincipalName());
                    if (azureUser.isValid()) {
                        azureUserResourceCache.put(
                                azureUser.getEmployeeId() != null
                                        ? azureUser.getEmployeeId()
                                        : azureUser.getStudentId(),
                                azureUser
                        );
                        log.debug("Saved to cache: {}", azureUser.getUserPrincipalName());
                    }
                    else {
                        log.debug("Not saved, missing employeeId or studentId: {} with azureID : {}",
                                azureUser.getUserPrincipalName(), azureUser.getId());
                    }
                }
        ).createContainer(topic("azureuser"));

    }


    @Bean
    ConcurrentMessageListenerContainer<String,User> userResourceEntityConsumer(
            FintCache<String, User> publishUserCache
    ){
        return createRecordListenerFactory(
                User.class,
                consumerRecord -> publishUserCache.put(
                        consumerRecord.value().getResourceId(),
                        consumerRecord.value()
                )
        ).createContainer(topic("user"));
    }

}
