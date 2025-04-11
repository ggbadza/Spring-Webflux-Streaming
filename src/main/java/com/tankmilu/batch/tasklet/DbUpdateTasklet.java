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
        return map.values().stream()
                .filter(e -> !"U".equals(e.getChangeCd()) && !"N".equals(e.getChangeCd()))
                .collect(Collectors.toList());
    }



}