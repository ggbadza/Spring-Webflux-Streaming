package com.tankmilu.webflux.service;

import com.tankmilu.webflux.record.ContentsReponse;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentsService {

    private final ContentsObjectRepository contentsObjectRepository;

    public Mono<ContentsReponse> getContentsInfo(Long contentsId) {
        return contentsObjectRepository.findById(contentsId)
                .map(contentsObject -> new ContentsReponse(
                        contentsObject.getContentsId(),
                        contentsObject.getTitle(),
                        contentsObject.getDescription(),
                        contentsObject.getThumbnailPath(),
                        contentsObject.getType(),
                        contentsObject.getFolderId()
                ));
    }
}
