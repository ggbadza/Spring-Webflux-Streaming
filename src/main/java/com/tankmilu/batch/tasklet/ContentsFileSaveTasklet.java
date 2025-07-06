package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ContentsFileSaveTasklet implements Tasklet {

    private final ContentsFileRepository fileRepository;
    private final ContentsObjectRepository contentsObjectRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("ContentsFileSaveTasklet 시작");

        // JobExecutionContext에서 각 리스트 가져오기
        @SuppressWarnings("unchecked")
        List<ContentsFileEntity> filesToInsert = (List<ContentsFileEntity>) chunkContext
                .getStepContext()
                .getJobExecutionContext()
                .get("filesToInsert");

        @SuppressWarnings("unchecked")
        List<ContentsFileEntity> filesToUpdate = (List<ContentsFileEntity>) chunkContext
                .getStepContext()
                .getJobExecutionContext()
                .get("filesToUpdate");

        @SuppressWarnings("unchecked")
        List<ContentsFileEntity> filesToDelete = (List<ContentsFileEntity>) chunkContext
                .getStepContext()
                .getJobExecutionContext()
                .get("filesToDelete");

        @SuppressWarnings("unchecked")
        List<ContentsObjectEntity> contentsToUpdate = (List<ContentsObjectEntity>) chunkContext
                .getStepContext()
                .getJobExecutionContext()
                .get("contentsToUpdate");

        // 리스트 체크 및 초기화 로직 생략...

        log.info("저장 작업 시작: 추가 {}, 수정 {}, 삭제 {}",
                filesToInsert.size(), filesToUpdate.size(), filesToDelete.size());

        try {
            // 리액티브 체인 구성
            Mono<Void> operations = Mono.empty();

            // 1. 신규 파일 엔티티 처리
            if (!filesToInsert.isEmpty()) {
                operations = operations.then(fileRepository.saveAll(filesToInsert).then());
                log.info("신규 파일 엔티티 등록 완료");
            }

            // 2. 수정 파일 엔티티 처리
            if (!filesToUpdate.isEmpty()) {
                operations = operations.then(fileRepository.saveAll(filesToUpdate).then());
                log.info("수정 파일 엔티티 등록 완료");
            } 

            // 3. 삭제 엔티티 처리
            if (!filesToDelete.isEmpty()) {
                operations = operations.then(fileRepository.deleteAll(filesToDelete));
                log.info("삭제 파일 엔티티 등록 완료");
            }

            // 4. 수정 컨텐츠 엔티티 처리
            if (!contentsToUpdate.isEmpty()) {
                operations = operations.then(contentsObjectRepository.saveAll(contentsToUpdate).then());
                log.info("수정 컨텐츠 엔티티 등록 완료");
            }

            // 트랜잭션 경계 내에서 실행하고 완료 대기
            operations
                    .as(transactionalOperator::transactional)
                    .doOnSuccess(v -> log.info("트랜잭션 처리 완료"))
                    .doOnError(e -> log.error("트랜잭션 처리 중 오류: ", e))
                    .block();

            log.info("ContentsFileSaveTasklet 종료");
            return RepeatStatus.FINISHED;
        } catch (Exception e) {
            log.error("파일 엔티티 처리 중 오류 발생: ", e);
            throw e;
        }
    }
}