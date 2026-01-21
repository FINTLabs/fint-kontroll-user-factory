package no.fintlabs.user;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.cache.FintCache;
import no.fintlabs.kafka.entity.EntityProducer;
import no.fintlabs.kafka.entity.EntityProducerFactory;
import no.fintlabs.kafka.entity.EntityProducerRecord;
import no.fintlabs.kafka.entity.topic.EntityTopicNameParameters;
import no.fintlabs.kafka.entity.topic.EntityTopicService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserEntityProducerService {
    private final FintCache<String, User> publishedUserCache;
    private final EntityProducer<User> entityProducer;
    private final EntityTopicNameParameters entityTopicNameParameters;

    public UserEntityProducerService(
            EntityProducerFactory entityProducerFactory,
            EntityTopicService entityTopicService,
            FintCache<String, User> publishedUserCache
    ) {
        this.publishedUserCache = publishedUserCache;
        entityProducer = entityProducerFactory.createProducer(User.class);
        entityTopicNameParameters = EntityTopicNameParameters
                .builder()
                .resource("user")
                .build();
        entityTopicService.ensureTopic(entityTopicNameParameters, 0);
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
        entityProducer.send(
                EntityProducerRecord.<User>builder()
                        .topicNameParameters(entityTopicNameParameters)
                        .key(key)
                        .value(user)
                        .build()
        );
    }
}
