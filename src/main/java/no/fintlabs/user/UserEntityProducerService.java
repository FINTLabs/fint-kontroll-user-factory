package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.novari.kafka.producing.ParameterizedProducerRecord;
import no.novari.kafka.producing.ParameterizedTemplate;
import no.novari.kafka.producing.ParameterizedTemplateFactory;
import no.novari.kafka.topic.EntityTopicService;
import no.novari.kafka.topic.configuration.EntityCleanupFrequency;
import no.novari.kafka.topic.configuration.EntityTopicConfiguration;
import no.novari.kafka.topic.name.EntityTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class UserEntityProducerService {
    private final FintCache<String, User> publishedUserCache;
    private final ParameterizedTemplate<User> parameterizedTemplate;
    private final EntityTopicNameParameters entityTopicNameParameters;

    public UserEntityProducerService(
            ParameterizedTemplateFactory parameterizedTemplateFactory,
            EntityTopicService entityTopicService,
            FintCache<String, User> publishedUserCache
    ) {
        this.publishedUserCache = publishedUserCache;
        parameterizedTemplate = parameterizedTemplateFactory.createTemplate(User.class);
        entityTopicNameParameters = EntityTopicNameParameters
                .builder()
                .topicNamePrefixParameters(
                        TopicNamePrefixParameters.stepBuilder()
                                .orgIdApplicationDefault()
                                .domainContextApplicationDefault()
                                .build()
                )
                .resourceName("user")
                .build();
        entityTopicService.createOrModifyTopic(
                entityTopicNameParameters,
                EntityTopicConfiguration.stepBuilder()
                        .partitions(1)
                        .lastValueRetainedForever()
                        .nullValueRetentionTime(Duration.ofDays(7))
                        .cleanupFrequency(EntityCleanupFrequency.NORMAL)
                        .build()
        );
    }

    public List<User> publishChangedUsers(List<User> users) {

        return users
                .stream()
                .filter(user -> publishedUserCache
                        .getOptional(user.getResourceId())
                        .map(publishedUserHash -> publishedUserHash.hashCode() != user.hashCode())
                        .orElse(true)
                )
                .peek(this::publishChangedUsers)
                .toList();

    }

    private void publishChangedUsers(User user) {
        String key = user.getResourceId();
        parameterizedTemplate.send(
                ParameterizedProducerRecord.<User>builder()
                        .topicNameParameters(entityTopicNameParameters)
                        .key(key)
                        .value(user)
                        .build()
        );
    }
}
