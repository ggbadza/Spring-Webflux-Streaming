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
        // 컨텐츠 타입(type)이 null 인 경우 전체 컨텐츠의 최신 수정 순서
        if (type == null) {
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
        // 부모 폴더 id(pid)이 null 혹은 0인 경우 해당 컨텐츠의 전체 최신 수정 순서
        } else if (pid == null || pid.equals(0L)) {
            return contentsObjectRepository.findTop20ByTypeEqualsOrderByModifiedAtDesc(type)
                    .map(contentsObject -> new ContentsResponse(
                            contentsObject.getContentsId(),
                            contentsObject.getTitle(),
                            contentsObject.getDescription(),
                            contentsObject.getThumbnailUrl(),
                            contentsObject.getPosterUrl(),
                            contentsObject.getType(),
                            contentsObject.getFolderId()
                    ));
        // 그 외의 경우 각 컨텐츠의 특정 폴더 내부 순서로 리턴
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
