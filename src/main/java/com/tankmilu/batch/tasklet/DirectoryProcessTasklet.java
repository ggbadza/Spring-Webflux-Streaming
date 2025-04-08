package com.tankmilu.batch.tasklet;

import com.tankmilu.batch.repository.folder.FolderTreeRepository;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Path;
import java.util.Map;

@AllArgsConstructor
public class DirectoryProcessTasklet implements Tasklet {

    private final Path rootPath;
    private final FolderTreeRepository<?> repository;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // ExecutionContext에서 데이터 가져오기
        Map<Long, FolderTreeEntity> folderMap =
                (Map<Long, FolderTreeEntity>) chunkContext.getStepContext()
                        .getStepExecution()
                        .getJobExecution()
                        .getExecutionContext()
                        .get("folderMap");

        // 디렉토리 처리 로직 구현
        processDirectory(rootPath, folderMap);

        // ExecutionContext에 저장
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("folderMap", folderMap);

        return RepeatStatus.FINISHED;
    }

    private void processDirectory(Path dir, Map<Long, FolderTreeEntity> map) {
        // 기존 디렉토리 처리 로직
    }
}