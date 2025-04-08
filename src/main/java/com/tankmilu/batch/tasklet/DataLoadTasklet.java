package com.tankmilu.batch.tasklet;

import com.tankmilu.batch.repository.folder.FolderTreeRepository;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class DataLoadTasklet implements Tasklet {

    private final FolderTreeRepository<?> repository;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // DB에서 데이터 로드
        List<? extends FolderTreeEntity> entities = repository.findAll();
        Map<Long, FolderTreeEntity> folderMap = new HashMap<>();

        // 엔티티를 map에 저장
        for (FolderTreeEntity entity : entities) {
            folderMap.put(entity.getId(), entity);
        }

        // ExecutionContext에 저장
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("folderMap", folderMap);

        return RepeatStatus.FINISHED;
    }
}
