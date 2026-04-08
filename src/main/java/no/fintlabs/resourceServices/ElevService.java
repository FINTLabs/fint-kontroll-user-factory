package no.fintlabs.resourceServices;

import lombok.extern.slf4j.Slf4j;
import no.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ElevService {
    private final FintCache<String, ElevResource> elevResourceCache;


    public ElevService(
            FintCache<String, ElevResource> elevResourceCache
    ) {
        this.elevResourceCache = elevResourceCache;
    }

    public List<ElevResource> getAllEleverWithElevforhold(Date currentTime){
        List<ElevResource> elevResources = elevResourceCache
                .getAllDistinct()
                .stream()
                .filter(elevResource -> !elevResource.getElevforhold().isEmpty())
                .toList();
        return elevResources;
    }

}
