package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.enums.SubscriptionCodeEnum;
import com.tankmilu.webflux.es.document.ContentsObjectDocument;
import com.tankmilu.webflux.es.repository.ContentsObjectDocumentRepository;
import com.tankmilu.webflux.exception.ContentsNotFoundException;
import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.UserContentsRecommendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentsService {

    private final ContentsObjectRepository contentsObjectRepository;

    private final ContentsFileRepository contentsFileRepository;

    private final UserContentsRecommendRepository userContentsRecommendRepository;

    private final ContentsObjectDocumentRepository contentsObjectDocumentRepository;

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
        return userContentsRecommendRepository.findByUserIdOrderByRecommendSeq(userId)
                // 데이터 미 존재 시, 기본값 "0"으로 받아오도록
                .switchIfEmpty(userContentsRecommendRepository.findByUserIdOrderByRecommendSeq("0"))
                // concatMap으로 Flux 순서 유지
                .concatMap(recommend -> {
                    Flux<ContentsResponse> contentsFlux = getContentsInfoRecently(recommend.getContentsType(), recommend.getFolderId());
                    return contentsFlux.collectList()
                            .map(contentsList -> new RecommendContentsResponse(
                                    recommend.getUserId(),
                                    recommend.getRecommendSeq(),
                                    recommend.getDescription(),
                                    contentsList
                            ));
                });
    }

    public Flux<FileInfoSummaryResponse> getContentsFiles(Long contentsId) {
        return contentsFileRepository.findByContentsIdOrderByFileName(contentsId)
                .map(fileEntity -> {
                    // FileInfoSummaryResponse로 변환하는 로직
                    return new FileInfoSummaryResponse(
                            fileEntity.getId(),
                            fileEntity.getFileName(),
                            fileEntity.getContentsId(),
                            StringUtils.hasText(fileEntity.getSubtitlePath()),
                            fileEntity.getResolution(),
                            fileEntity.getCreatedAt()
                    );
                });
    }

    public Mono<ContentsInfoWithFilesResponse> getContentsInfoWithVideoFiles(Long fileId) {
        return contentsFileRepository.findAllFilesSharingSameContentAsFileId(fileId)
                .collectList()
                .flatMap(relatedFileEntities -> {
                    if (relatedFileEntities.isEmpty()) {
                        // 관련된 파일이 하나도 없으면, 컨텐츠 ID를 알 수 없으므로 에러 처리
                        return Mono.error(new ContentsNotFoundException("(fileId: " + fileId + ") 에 대한 파일이 없습니다.", "1101", HttpStatus.NO_CONTENT));
                    }

                    // 조회된 파일 목록에서 contentsId를 추출.
                    Long sharedContentsId = relatedFileEntities.getFirst().getContentsId();
                    if (sharedContentsId == null) {
                        return Mono.error(new ContentsNotFoundException("(fileId: " + fileId + ") 에 대한 컨텐츠가 없습니다.", "1102", HttpStatus.NO_CONTENT));
                    }


                    // List<ContentsFileEntity>를 List<FileInfoSummaryResponse>로 변환.
                    List<FileInfoSummaryResponse> filesInfoList = relatedFileEntities.stream()
                            .map(entity -> new FileInfoSummaryResponse(
                                    entity.getFileId(),
                                    entity.getFileName(),
                                    entity.getContentsId(),
                                    (entity.getSubtitlePath() != null && !entity.getSubtitlePath().isEmpty()), // 자막 존재 여부만 체크
                                    entity.getResolution(),
                                    entity.getCreatedAt()
                            ))
                            .collect(Collectors.toList());

                    // 추출된 contentsId를 사용하여 ContentsObjectEntity (컨텐츠 정보)를 조회
                    Mono<ContentsObjectEntity> contentsObjectMono = contentsObjectRepository.findById(sharedContentsId);

                    //  ContentsObjectEntity 정보와 List<FileInfoSummaryResponse>를 조합하여 ContentsInfoWithFilesResponse를 생성
                    return contentsObjectMono
                            .map(contentsObject -> new ContentsInfoWithFilesResponse(
                                    contentsObject.getId(), // 또는 sharedContentsId 사용
                                    contentsObject.getTitle(),
                                    contentsObject.getDescription(),
                                    contentsObject.getThumbnailUrl(),
                                    contentsObject.getPosterUrl(),
                                    contentsObject.getType(),
                                    filesInfoList
                            ))
                            .switchIfEmpty(Mono.error(new ContentsNotFoundException("(contentsId: "+ sharedContentsId +") 에 대한 컨텐츠 정보를 찾을 수 없습니다.", "1103", HttpStatus.NO_CONTENT)));
                });
    }

    public Flux<ContentsSearchResponse> searchContentsByQuery(String query) {
        log.info("Searching across title, description, and keywords for query: {}", query);

        final int MAX_RESULTS = 30;

        // 1. title 필드에서 검색
        Flux<ContentsObjectDocument> titleSearchResults = contentsObjectDocumentRepository
                .searchByTitle(query)
                .doOnNext(doc -> log.debug("타이틀 검색 결과: {}", doc.getTitle()))
                .doOnError(e -> log.error("쿼리 '{}' 에 대한 타이틀 검색 실패: {}", query, e.getMessage(), e));

        // 2. description 및 keywords 필드에서 검색
        Flux<ContentsObjectDocument> descriptionKeywordsSearchResults = contentsObjectDocumentRepository
                .searchByQueryInDescriptionAndKeywords(query)
                .doOnNext(doc -> log.debug("설명/키워드 검색 결과: {}", doc.getTitle()))
                .doOnError(e -> log.error("쿼리 '{}' 에 대한 설명/키워드 검색 실패: {}", query, e.getMessage(), e));

        // 3. 두 검색 결과를 합치고 중복 제거 후 DTO로 변환
        return Flux.concat(titleSearchResults, descriptionKeywordsSearchResults)
                .distinct(ContentsObjectDocument::getContentsId)
//                .log("AfterDistinct")
                .take(MAX_RESULTS)
                .map(document -> new ContentsSearchResponse(
                        document.getContentsId(),
                        document.getTitle(),
                        document.getDescription(),
                        document.getType(),
                        document.getThumbnailUrl(),
                        document.getModifiedAt()
                ))
                .doOnComplete(() -> log.info("쿼리 '{}' 검색 완료.", query))
                .doOnError(e -> log.error("쿼리 '{}' 검색 실패: {}", query, e.getMessage(), e));
    }


}
