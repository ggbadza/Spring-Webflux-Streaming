package com.tankmilu.batch.config;

import com.tankmilu.batch.tasklet.FolderDataLoadTasklet;
import com.tankmilu.batch.tasklet.FolderEntityUpdateTasklet;
import com.tankmilu.batch.tasklet.FolderDirectoryProcessTasklet;
import com.tankmilu.webflux.entity.folder.AnimationFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.DramaFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.MovieFolderTreeEntity;
import com.tankmilu.webflux.repository.folder.AnimationFolderTreeRepository;
import com.tankmilu.webflux.repository.folder.DramaFolderTreeRepository;
import com.tankmilu.webflux.repository.folder.MovieFolderTreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.nio.file.Path;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FolderSyncConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionalOperator transactionalOperator;

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
                .next(dbUpdateStep(null,null))               // Step3: DB 업데이트
                .build();
    }

    // Step 1 : DB에서 데이터 로드
    @Bean
    @JobScope
    public Step dataLoadStep(
            @Value("#{jobParameters['type']}") String type) {
            log.info("dataLoadStep!@#");
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
    @JobScope
    public Step dbUpdateStep(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['deleteYn']}") String deleteYn) {
        return new StepBuilder("dbUpdateStep", jobRepository)
                .tasklet(dbUpdateTasklet(type, deleteYn), transactionManager)
                .build();
    }

    /*
    Tasklet 정의
     */

    @Bean
    @StepScope
    public FolderDataLoadTasklet dataLoadTasklet(
            @Value("#{jobParameters['type']}") String type) {
        return switch (type) {
            case "anime" -> new FolderDataLoadTasklet(animationFolderTreeRepository);
            case "movie" -> new FolderDataLoadTasklet(movieFolderTreeRepository);
            case "drama" -> new FolderDataLoadTasklet(dramaFolderTreeRepository);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    @Bean
    @StepScope
    public FolderDirectoryProcessTasklet directoryProcessTasklet(
            @Value("#{jobParameters['directoryPath']}") String path,
            @Value("#{jobParameters['type']}") String type) {

        // 엔티티 팩토리 메소드를 람다 함수로 전달
        return switch (type) {
            case "anime" -> new FolderDirectoryProcessTasklet<AnimationFolderTreeEntity>(Path.of(path),
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
            case "movie" -> new FolderDirectoryProcessTasklet<MovieFolderTreeEntity>(Path.of(path),
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
            case "drama" -> new FolderDirectoryProcessTasklet<DramaFolderTreeEntity>(Path.of(path),
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
    public FolderEntityUpdateTasklet<?> dbUpdateTasklet(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['deleteYn']}") String deleteYn) {

        return switch (type) {
            case "anime" -> new FolderEntityUpdateTasklet<>(animationFolderTreeRepository, transactionalOperator, deleteYn);
            case "movie" -> new FolderEntityUpdateTasklet<>(movieFolderTreeRepository, transactionalOperator, deleteYn);
            case "drama" -> new FolderEntityUpdateTasklet<>(dramaFolderTreeRepository, transactionalOperator, deleteYn);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

}
