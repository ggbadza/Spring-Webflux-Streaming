package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class DbUpdateTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final FolderTreeRepository<T> repository;

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

        // DB 업데이트 로직
        repository.saveAll(identifyUpdates(folderMap)).collectList().block();


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