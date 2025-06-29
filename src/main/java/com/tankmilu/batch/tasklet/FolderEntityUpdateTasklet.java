package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class FolderEntityUpdateTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final FolderTreeRepository<T> repository;
    private final TransactionalOperator transactionalOperator; // ReactiveTransactionManager 는 내부 주입
    private final String deleteYn;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // ExecutionContext에서 데이터 가져오기
        Map<Long, T> folderMap = (Map<Long, T>)
                chunkContext.getStepContext()
                        .getStepExecution()
                        .getJobExecution()
                        .getExecutionContext()
                        .get("folderMap");

        // ExecutionContext에서 데이터 가져오기
        List<T> folderToDelete = (List<T>)
                chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .get("folderToDelete");


        // Null 체크 및 기본값 설정
        if (folderMap == null) {
            log.warn("ExecutionContext에서 'folderMap'이 null로 로드되었습니다. 업데이트 작업이 수행되지 않습니다.");
            folderMap = new java.util.HashMap<>(); // 안전을 위해 빈 맵 초기화
        }
        if (folderToDelete == null || !deleteYn.equals("Y")) {
            log.warn("ExecutionContext에서 'folderToDelete'가 null로 로드되었습니다. 삭제 작업이 수행되지 않습니다.");
            folderToDelete = new java.util.ArrayList<>(); // 안전을 위해 빈 리스트 초기화
        }

        // 리액티브 체인 구성
        Mono<Void> operations = Mono.empty();

        // 1. 신규 엔티티 처리
        if (!folderMap.isEmpty()) {
            operations = operations.then(repository.saveAll(identifyUpdates(folderMap)).then());
        }


        // 2. 삭제 엔티티 처리
        if (!folderToDelete.isEmpty()) {
            operations = operations.then(repository.deleteAll(folderToDelete));
        }

        // 트랜잭션 경계 내에서 실행하고 완료 대기
        operations
                .as(transactionalOperator::transactional)
                .doOnSuccess(v -> log.info("트랜잭션 처리 완료"))
                .doOnError(e -> log.error("트랜잭션 처리 중 오류: ", e))
                .block();

        return RepeatStatus.FINISHED;
    }


    private List<T> identifyUpdates(Map<Long, T> map) {
        log.info("identifyUpdates 시작. 맵 크기: {}", map.size());

        List<T> result = map.values().stream()
                .filter(e -> {
                    log.debug("Entity ID: {}, ChangeCd: {}", e.getFolderId(), e.getChangeCd());
                    return "Y".equals(e.getChangeCd()) || "N".equals(e.getChangeCd());
                })
                .collect(Collectors.toList());

        log.info("필터링된 엔티티 수: {}", result.size());
        return result;
    }


}