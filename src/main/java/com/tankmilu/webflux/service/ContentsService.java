package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.entity.UserContentsFollowingEntity;
import com.tankmilu.webflux.enums.SubscriptionCodeEnum;
import com.tankmilu.webflux.es.document.ContentsObjectDocument;
import com.tankmilu.webflux.es.repository.ContentsObjectDocumentRepository;
import com.tankmilu.webflux.exception.ContentsNotFoundException;
import com.tankmilu.webflux.record.*;
import com.tankmilu.webflux.repository.*;
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
    private final UserContentsFollowingRepository userContentsFollowingRepository;
    private final FeaturedBannersRepository featuredBannersRepository;

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

    public Mono<Boolean> registerFollowing(String userId, Long contentsId){
        return userContentsFollowingRepository.findMaxFollowingSeqByUserId(userId)
                .defaultIfEmpty(0) // 현재 user_id에 팔로우 데이터가 없으면 0부터 시작 (nextSeq는 1이 됨)
                .flatMap(maxSeq -> {
                    Integer nextSeq = maxSeq + 1; // 다음 following_seq 계산

                    UserContentsFollowingEntity newFollowing = UserContentsFollowingEntity.builder()
                            .userId(userId)
                            .contentsId(contentsId)
                            .followingSeq(nextSeq) // 계산된 following_seq 설정
                            .createdAt(java.time.LocalDateTime.now()) // 현재 시간으로 생성 시간 설정
                            .build();

                    // 엔티티를 저장하고, 성공하면 true를 반환
                    return userContentsFollowingRepository.save(newFollowing)
                            .thenReturn(true);
                });
    }

    public Mono<Boolean> deleteFollowing(String userId, Long contentsId){
        return userContentsFollowingRepository.deleteByUserIdAndContentsId(userId, contentsId)
                .thenReturn(true)
                .onErrorResume(e -> {
                    System.err.println("즐겨찾기 데이터를 삭제하는데 오류 발생 userId: " + userId + ", contentsId: " + contentsId + ". Error: " + e.getMessage());
                    return Mono.just(false); // 실패 시 false Mono 방출
                });
    }


    public Flux<ContentsResponse> getFollowingContents(String userId) {
        return userContentsFollowingRepository.findByUserIdOrderByFollowingSeq(userId) // 1. following_seq 순서로 Flux<UserContentsFollowingEntity>를 가져옴
                .flatMapSequential(userFollowing -> getContentsInfo(userFollowing.getContentsId()));
    }

    public Mono<Boolean> isFollowingContent(String userId, Long contentsId){
        return userContentsFollowingRepository.existsByUserIdAndContentsId(userId, contentsId);
    }

    public Flux<FeaturedBannersResponse> getFeaturedBanners(){
        return featuredBannersRepository.findAllByOrderBySequenceIdAsc()
                .map(FeaturedBannersResponse::fromEntity); // FeaturedBannersResponse로 맵핑 메소드 참조
    }

}
