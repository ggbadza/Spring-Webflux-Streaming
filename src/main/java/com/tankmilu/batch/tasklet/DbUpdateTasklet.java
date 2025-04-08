package com.tankmilu.batch.tasklet;

import com.tankmilu.batch.repository.folder.FolderTreeRepository;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class DbUpdateTasklet implements Tasklet {

    private final FolderTreeRepository<? extends FolderTreeEntity> repository;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // ExecutionContext에서 데이터 가져오기
        Map<Long, FolderTreeEntity> folderMap = (Map<Long, FolderTreeEntity>)
                chunkContext.getStepContext()
                        .getStepExecution()
                        .getJobExecution()
                        .getExecutionContext()
                        .get("folderMap");

        // DB 업데이트 로직
//        repository.deleteByIdIn(identifyDeletions(folderMap));
//        repository.saveAll(identifyUpdates(folderMap));


        return RepeatStatus.FINISHED;
    }

    private List<? extends FolderTreeEntity> identifyUpdates(Map<Long, FolderTreeEntity> map) {
        return map.values().stream()
                .filter(e -> !"U".equals(e.getChangeCd()))
                .collect(Collectors.toList());
    }

//    private List<Long> identifyDeletions(Map<Long, FolderTreeEntity> map) {
//        return repository.findAllIds().stream()
//                .filter(id -> !map.containsKey(id))
//                .collect(Collectors.toList());
//    }
}