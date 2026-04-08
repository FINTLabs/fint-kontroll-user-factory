package no.fintlabs.links;

import no.fint.model.resource.FintLinks;
import no.fint.model.resource.Link;
import org.apache.kafka.common.protocol.types.Field;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceLinkUtil {

    public static String getFirstSelfLink(FintLinks resource) {
        return resource.getSelfLinks()
                .stream()
                .findFirst()
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .orElseThrow(() -> NoSuchLinkException.noSelfLink(resource));
    }

    public static List<String> getSelfLinks(FintLinks resource) {
        return resource.getSelfLinks()
                .stream()
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .collect(Collectors.toList());
    }

    public static String getFirstLink(Supplier<List<Link>> linkProducer, FintLinks resource, String linkedResourceName) {
        return Optional.ofNullable(linkProducer.get())
                .map(Collection::stream)
                .flatMap(Stream::findFirst)
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase)
                .orElseThrow(() -> NoSuchLinkException.noLink(resource, linkedResourceName));
    }

    public static Optional<String> getOptionalFirstLink(Supplier<List<Link>> linkProducer) {
        return Optional.ofNullable(linkProducer.get())
                .map(Collection::stream)
                .flatMap(Stream::findFirst)
                .map(Link::getHref)
                .map(ResourceLinkUtil::systemIdToLowerCase);
    }

    public static String systemIdToLowerCase(String path) {
        return path.replace("systemId", "systemid");
    }

    public static String organisasjonsIdToLowerCase(String path) {return path.replace("organisasjonsId", "organisasjonsid");}

    public static String organisasjonsKodeToLowerCase(String path) {return path.replace("organisasjonsKode", "organisasjonskode");}

}
