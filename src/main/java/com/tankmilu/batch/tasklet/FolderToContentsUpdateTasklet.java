package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.ContentsKeywordsEntity;
import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.es.document.ContentsObjectDocument;
import com.tankmilu.webflux.es.repository.ContentsObjectDocumentRepository;
import com.tankmilu.webflux.repository.ContentsKeywordsRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class FolderToContentsUpdateTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final FolderTreeRepository<T> folderRepository;
    private final ContentsObjectRepository contentsRepository;
    private final ContentsKeywordsRepository contentsKeywordsRepository;
    private final ContentsObjectDocumentRepository contentsObjectDocumentRepository;
    private final String contentType; // "anime", "movie", "drama" 등
    private final TransactionalOperator transactionalOperator; // 배치 내부 DB 작업 관리용 트랜잭션 오퍼레이터

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {
        log.info("FolderToContentsUpdateTasklet 시작 - 타입: {}", contentType);

        // 1. FolderTreeEntity에서 데이터 로드 (폴더 ID로 그룹화)
        List<T> folderEntities = folderRepository.findAll()
                .collectList()
                .block();

        if (folderEntities == null || folderEntities.isEmpty()) {
            log.info("폴더 데이터가 없습니다. 타입: {}", contentType);
            return RepeatStatus.FINISHED;
        }

        Map<Long, T> allFoldersMap  = new HashMap<>();
        for (T folder : folderEntities) {
            allFoldersMap .put(folder.getFolderId(), folder);
        }

        // hasFiles가 true이면서 부모 폴더중에 true가 없는 폴더만 처리
        Map<Long, T> folderEntityMap = new HashMap<>();

        for (T folder : folderEntities) {
            if (folder.getHasFiles() != null && folder.getHasFiles()) {
                // 부모 폴더 체인에 hasFiles=true인 폴더가 있는지 확인
                boolean includeFolder = true;
                Long parentId = folder.getParentFolderId();

                // 부모 폴더를 타고 올라가며 체크
                while (parentId != null) {
                    T parentFolder = allFoldersMap.get(parentId);
                    if (parentFolder == null) {
                        break; // 부모 폴더를 찾을 수 없는 경우
                    }

                    // 부모 폴더가 hasFiles=true라면 현재 폴더는 제외
                    if (parentFolder.getHasFiles() != null && parentFolder.getHasFiles()) {
                        includeFolder = false;
                        break;
                    }

                    // 다음 부모로 이동
                    parentId = parentFolder.getParentFolderId();
                }

                // 모든 조건을 만족하면 맵에 추가
                if (includeFolder) {
                    folderEntityMap.put(folder.getFolderId(), folder);
                }
            }
        }


        log.info("로드된 폴더 엔티티 수: {}", folderEntities.size());

        // 2. ContentsObjectEntity 로드
        List<ContentsObjectEntity> existingContents = contentsRepository.findAll()
                .collectList()
                .block();

        // --- Elasticsearch 인덱스 비우기 로직 시작 ---
//        log.info("Elasticsearch 인덱스 'contents'의 모든 도큐먼트를 비웁니다.");
//        try {
//            // deleteAll()은 Mono<Void>를 반환하며, 모든 삭제 작업이 완료될 때까지 블록합니다.
//            contentsObjectDocumentRepository.deleteAll().block();
//            log.info("Elasticsearch 인덱스 'contents' 비우기 완료.");
//        } catch (Exception e) {
//            log.error("Elasticsearch 인덱스를 비우는 중 오류 발생: {}", e.getMessage(), e);
//            // 오류 발생 시에도 작업을 계속 진행할지 여부는 비즈니스 로직에 따라 결정
//            // return RepeatStatus.FINISHED; // 오류 시 즉시 종료하려면 주석 해제
//        }
        // --- Elasticsearch 인덱스 비우기 로직 끝 ---

        // ContentsObjectEntity를 이용해서 ContentsKeywordsEntity들을 가져오고, 그것들을 ContentsObjectDocument에 삽입하는 로직
        if (existingContents != null && !existingContents.isEmpty()) {
            log.info("기존 ContentsObjectEntity들을 사용하여 Elasticsearch에 인덱싱 시작.");
            for (ContentsObjectEntity content : existingContents) {
                // 각 ContentsObjectEntity의 seriesId로 키워드 조회 (R2DBC)
                // block()을 사용하여 비동기 결과를 동기적으로 기다립니다.
                List<String> keywords = contentsKeywordsRepository.findBySeriesId(content.getSeriesId())
                        .map(ContentsKeywordsEntity::getKeyword)
                        .collectList() // Flux<String>을 Mono<List<String>>으로 변환
                        .block(); // 키워드 리스트를 얻을 때까지 블록

                // 키워드가 null이거나 비어있지 않다면 ContentsObjectDocument 생성 및 저장
                ContentsObjectDocument document = ContentsObjectDocument.builder()
                        .contentsId(content.getContentsId())
                        .title(content.getTitle())
                        .description(content.getDescription())
                        .type(content.getType())
                        .thumbnailUrl(content.getThumbnailUrl())
                        .modifiedAt(content.getModifiedAt())
                        .keywords(keywords) // 조회된 키워드 리스트 할당
                        .build();

                // Elasticsearch에 ContentsObjectDocument 저장 (UPSERT 기능)
                // block()을 사용하여 비동기 저장을 동기적으로 기다립니다.
                contentsObjectDocumentRepository.save(document)
                        .block(); // 저장 완료될 때까지 블록
                log.debug("ContentsObjectDocument 저장 완료: ContentsId={}, Title='{}'", content.getContentsId(), content.getTitle());
            }
            log.info("기존 ContentsObjectEntity들을 사용하여 Elasticsearch 인덱싱 완료.");
        } else {
            log.info("인덱싱할 기존 ContentsObjectEntity가 없습니다.");
        }


        // ContentsObjectEntity를 폴더 ID로 그룹화 수행
        Map<Long, ContentsObjectEntity> existingContentsMap = new HashMap<>();
        if (existingContents != null) {
            for (ContentsObjectEntity content : existingContents) {
                if (content.getFolderId() != null && content.getType().equals(contentType) ) {
                    existingContentsMap.put(content.getFolderId(), content);
                }
            }
        }

        log.info("기존 콘텐츠 엔티티 수: {}", existingContentsMap.size());


        // 3-a. 폴더가 있으나 컨텐츠가 없는 폴더에 대해 ContentsObjectEntity 생성

        List<ContentsObjectEntity> contentsToSave = new ArrayList<>();



        // folderEntityMap을 순회하면서 existingContentsMap에 없는 경우만 처리
        for (Map.Entry<Long, T> entry : folderEntityMap.entrySet()) {
            Long folderId = entry.getKey();
            T folder = entry.getValue();

            // existingContentsMap에 해당 폴더ID가 없는 경우에만 새 엔티티 생성
            if (!existingContentsMap.containsKey(folderId)) {
                ContentsObjectEntity newContent = createNewContentsEntity(folder);
                log.debug("새 콘텐츠 엔티티 생성: {}", folder.getName());
                contentsToSave.add(newContent);



            } else {
                log.debug("이미 콘텐츠가 존재하는 폴더: {}", folder.getName());
            }
        }

        log.info("저장할 새 콘텐츠 엔티티 수: {}", contentsToSave.size());


        // 3-b. 콘텐츠가 있으나 폴더가 없는 경우  ContentsObjectEntity 삭제 처리

        List<ContentsObjectEntity> contentsToDelete = new ArrayList<>();

        for (Map.Entry<Long, ContentsObjectEntity> entry : existingContentsMap.entrySet()) {
            Long folderId = entry.getKey();
            ContentsObjectEntity entity = entry.getValue();

            // existingContentsMap에 해당 폴더ID가 없는 경우에만 새 엔티티 생성
            if (!folderEntityMap.containsKey(folderId)) {
                log.debug("폴더 미존재. folderId '{}'에 해당하는 콘텐츠 엔티티 삭제: ({}){}", folderId,entity.getContentsId(),entity.getTitle());
                contentsToDelete.add(entity);
            } else {
                log.debug("이미 폴더가 존재하는 콘텐츠: ({}){}", entity.getContentsId(),entity.getTitle());
            }
        }

        log.info("삭제 할 콘텐츠 엔티티 수: {}", contentsToDelete.size());

        // 4. DB 저장 작업

        // 리액티브 체인 구성
        Mono<Void> operations = Mono.empty();

        if (!contentsToSave.isEmpty()) {
            operations=operations.then(contentsRepository.saveAll(contentsToSave).then());
            log.info("콘텐츠 엔티티 저장 작업 등록 완료");
        }

        if (!contentsToDelete.isEmpty()) {
            operations=operations.then(contentsRepository.deleteAll(contentsToDelete).then());
            log.info("콘텐츠 엔티티 삭제 작업 등록 완료");
        }
        operations
                .as(transactionalOperator::transactional)
                .doOnSuccess(v -> log.info("트랜잭션 처리 완료"))
                .doOnError(e -> log.error("트랜잭션 처리 중 오류: ", e))
                .block();

        return RepeatStatus.FINISHED;
    }

    private ContentsObjectEntity createNewContentsEntity(T folder) {
        // 현재 날짜에서 연도와 월 추출
        LocalDateTime now = LocalDateTime.now();
        String yearMonth = String.format("%d%02d", now.getYear(), now.getMonthValue());

        return ContentsObjectEntity.builder()
                .title(folder.getName())
                .releaseYM(yearMonth)
                .type(contentType)
                .folderId(folder.getFolderId())
                .modifiedAt(folder.getModifiedAt())
                .build();
    }
}
