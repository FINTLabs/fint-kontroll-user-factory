package no.fintlabs.resourceServices;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.novari.fint.model.resource.utdanning.elev.ElevResource;
import no.fintlabs.cache.FintCache;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElevService {
    private final FintCache<String, ElevResource> elevResourceCache;

    public List<ElevResource> getAllEleverWithElevforhold(){
        return elevResourceCache
                .getAllDistinct()
                .stream()
                .filter(elevResource -> !elevResource.getElevforhold().isEmpty())
                .toList();
    }

}
