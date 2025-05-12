package com.tankmilu.webflux.service;

import com.tankmilu.webflux.record.ContentsResponse;
import com.tankmilu.webflux.record.RecommendContentsResponse;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.UserContentsRecommendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentsService {

    private final ContentsObjectRepository contentsObjectRepository;

    private final UserContentsRecommendRepository userContentsRecommendRepository;

    public Mono<ContentsResponse> getContentsInfo(Long contentsId) {
        return contentsObjectRepository.findById(contentsId)
                .map(contentsObject -> new ContentsResponse(
                        contentsObject.getContentsId(),
                        contentsObject.getTitle(),
                        contentsObject.getDescription(),
                        contentsObject.getThumbnailUrl(),
                        contentsObject.getPosterUrl(),
                        contentsObject.getType(),
                        contentsObject.getFolderId()
                ));
    }

    public Flux<ContentsResponse> getContentsInfoRecently(String type, Long pid) {
        if (type == null || pid == null) {
            return contentsObjectRepository.findTop20ByOrderByModifiedAtDesc()
                    .map(contentsObject -> new ContentsResponse(
                            contentsObject.getContentsId(),
                            contentsObject.getTitle(),
                            contentsObject.getDescription(),
                            contentsObject.getThumbnailUrl(),
                            contentsObject.getPosterUrl(),
                            contentsObject.getType(),
                            contentsObject.getFolderId()
                    ));
        } else {
            return contentsObjectRepository.findContentsObjectEntitiesByTypeAndFolderIdRecursive(type, pid)
                    .map(contentsObject -> new ContentsResponse(
                            contentsObject.getContentsId(),
                            contentsObject.getTitle(),
                            contentsObject.getDescription(),
                            contentsObject.getThumbnailUrl(),
                            contentsObject.getPosterUrl(),
                            contentsObject.getType(),
                            contentsObject.getFolderId()
                    ));

        }
    }

    public Flux<RecommendContentsResponse> getRecommendContents(String userId) {
        return userContentsRecommendRepository.findByIdUserIdOrderByIdRecommendSeq(userId)
                // concatMap으로 Flux 순서 유지
                .concatMap(recommend -> {
                    Flux<ContentsResponse> contentsFlux = getContentsInfoRecently(recommend.getContentsType(), recommend.getFolderId());
                    return contentsFlux.collectList()
                            .map(contentsList -> new RecommendContentsResponse(
                                    recommend.getId().getUserId(),
                                    recommend.getId().getRecommendSeq(),
                                    recommend.getDescription(),
                                    contentsList
                            ));
                });
    }


}
