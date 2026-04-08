package no.fintlabs.links;

import no.fint.model.resource.FintLinks;

public class NoSuchLinkException extends RuntimeException {

    public static NoSuchLinkException noSelfLink(FintLinks resource) {
        return new NoSuchLinkException(String.format("No self link in resource=%s", resource.toString()));
    }

    public static NoSuchLinkException noLink(FintLinks resource, String linkedResourceName) {
        return new NoSuchLinkException(String.format("No link for '%s' in resource=%s", linkedResourceName, resource.toString()));
    }

    public NoSuchLinkException(String message) {
        super(message);
    }

}
