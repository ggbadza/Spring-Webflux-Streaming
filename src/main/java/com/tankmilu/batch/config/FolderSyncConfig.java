package com.tankmilu.batch.config;

import com.tankmilu.batch.repository.folder.AnimationFolderTreeRepository;
import com.tankmilu.batch.repository.folder.DramaFolderTreeRepository;
import com.tankmilu.batch.repository.folder.MovieFolderTreeRepository;
import com.tankmilu.batch.tasklet.DataLoadTasklet;
import com.tankmilu.batch.tasklet.DbUpdateTasklet;
import com.tankmilu.batch.tasklet.DirectoryProcessTasklet;
import com.tankmilu.webflux.entity.folder.AnimationFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.DramaFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.MovieFolderTreeEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class FolderSyncConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    // 레파지토리 주입
    private final AnimationFolderTreeRepository animationFolderTreeRepository;
    private final MovieFolderTreeRepository movieFolderTreeRepository;
    private final DramaFolderTreeRepository dramaFolderTreeRepository;

    @Bean
    public Job folderSyncJob() {
        return new JobBuilder("folderSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(dataLoadStep(null))     // Step1: 데이터 로드
                .next(directoryProcessStep(null,null))    // Step2: 디렉토리 처리
                .next(dbUpdateStep(null))               // Step3: DB 업데이트
                .build();
    }

    // Step 1 : DB에서 데이터 로드
    @Bean
    @JobScope
    public Step dataLoadStep(
            @Value("#{jobParameters['type']}") String type) {

        return new StepBuilder("dataLoadStep", jobRepository)
                .tasklet(dataLoadTasklet(type), transactionManager)
                .build();
    }


    // Step 2 : 디렉토리 검색
    @Bean
    @JobScope
    public Step directoryProcessStep(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['directoryPath']}") String path) {

        return new StepBuilder("directoryProcessStep", jobRepository)
                .tasklet(directoryProcessTasklet(type, path), transactionManager)
                .build();
    }

    // Step 3 : DB 업데이트
    @Bean
    public Step dbUpdateStep(
            @Value("#{jobParameters['type']}") String type) {
        return new StepBuilder("dbUpdateStep", jobRepository)
                .tasklet(dbUpdateTasklet(type), transactionManager)
                .build();
    }

    /*
    Tasklet 정의
     */

    @Bean
    @StepScope
    public DataLoadTasklet dataLoadTasklet(
            @Value("#{jobParameters['type']}") String type) {
        return switch (type) {
            case "anime" -> new DataLoadTasklet(animationFolderTreeRepository);
            case "movie" -> new DataLoadTasklet(movieFolderTreeRepository);
            case "drama" -> new DataLoadTasklet(dramaFolderTreeRepository);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    @Bean
    @StepScope
    public DirectoryProcessTasklet directoryProcessTasklet(
            @Value("#{jobParameters['directoryPath']}") String path,
            @Value("#{jobParameters['type']}") String type) {

        // 엔티티 팩토리 메소드를 람다 함수로 전달
        return switch (type) {
            case "anime" -> new DirectoryProcessTasklet<AnimationFolderTreeEntity>(Path.of(path),
                    (folderId, entityName, entityPath, parentId, subscriptionCode, createdAt, modifiedAt, hasFiles) ->
                            AnimationFolderTreeEntity.builder()
                                    .folderId(folderId)
                                    .name(entityName)
                                    .folderPath(entityPath)
                                    .parentFolderId(parentId)
                                    .subscriptionCode(subscriptionCode)
                                    .createdAt(createdAt)
                                    .modifiedAt(modifiedAt)
                                    .hasFiles(hasFiles)
                                    .isNewRecord(true)
                                    .build());
            case "movie" -> new DirectoryProcessTasklet<MovieFolderTreeEntity>(Path.of(path),
                    (folderId, entityName, entityPath, parentId, subscriptionCode, createdAt, modifiedAt, hasFiles) ->
                            MovieFolderTreeEntity.builder()
                                    .folderId(folderId)
                                    .name(entityName)
                                    .folderPath(entityPath)
                                    .parentFolderId(parentId)
                                    .subscriptionCode(subscriptionCode)
                                    .createdAt(createdAt)
                                    .modifiedAt(modifiedAt)
                                    .hasFiles(hasFiles)
                                    .isNewRecord(true)
                                    .build());
            case "drama" -> new DirectoryProcessTasklet<DramaFolderTreeEntity>(Path.of(path),
                    (folderId, entityName, entityPath, parentId, subscriptionCode, createdAt, modifiedAt, hasFiles) ->
                            DramaFolderTreeEntity.builder()
                                    .folderId(folderId)
                                    .name(entityName)
                                    .folderPath(entityPath)
                                    .parentFolderId(parentId)
                                    .subscriptionCode(subscriptionCode)
                                    .createdAt(createdAt)
                                    .modifiedAt(modifiedAt)
                                    .hasFiles(hasFiles)
                                    .isNewRecord(true)
                                    .build());
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    @Bean
    @StepScope
    @SuppressWarnings("unchecked")
    public DbUpdateTasklet<?> dbUpdateTasklet(
            @Value("#{jobParameters['type']}") String type) {

        return switch (type) {
            case "anime" -> new DbUpdateTasklet<>(animationFolderTreeRepository);
            case "movie" -> new DbUpdateTasklet<>(movieFolderTreeRepository);
            case "drama" -> new DbUpdateTasklet<>(dramaFolderTreeRepository);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

}
