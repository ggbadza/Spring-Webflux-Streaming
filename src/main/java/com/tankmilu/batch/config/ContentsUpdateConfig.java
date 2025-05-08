package com.tankmilu.batch.config;

import com.tankmilu.batch.tasklet.ContentsToFileUpdateTasklet;
import com.tankmilu.batch.tasklet.FolderToContentsUpdateTasklet;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ContentsUpdateConfig {


    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;


    // 레파지토리 주입
    private final AnimationFolderTreeRepository animationFolderTreeRepository;
    private final MovieFolderTreeRepository movieFolderTreeRepository;
    private final DramaFolderTreeRepository dramaFolderTreeRepository;
    private final ContentsObjectRepository contentsObjectRepository;
    private final ContentsFileRepository contentsFileRepository;

    // Job 1: FolderTree -> ContentsObject 업데이트 작업
    @Bean
    public Job folderToContentsJob() {
        return new JobBuilder("folderToContentsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(folderToContentsStep(null))
                .build();
    }

    @Bean
    @JobScope
    public Step folderToContentsStep(
            @Value("#{jobParameters['type']}") String type) {
        return new StepBuilder("folderToContentsStep", jobRepository)
                .tasklet(folderToContentsTasklet(type), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public FolderToContentsUpdateTasklet<?> folderToContentsTasklet(
            @Value("#{jobParameters['type']}") String type) {
        return switch (type) {
            case "anime" -> new FolderToContentsUpdateTasklet<>(
                    animationFolderTreeRepository, contentsObjectRepository, "anime");
            case "movie" -> new FolderToContentsUpdateTasklet<>(
                    movieFolderTreeRepository, contentsObjectRepository, "movie");
            case "drama" -> new FolderToContentsUpdateTasklet<>(
                    dramaFolderTreeRepository, contentsObjectRepository, "drama");
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    // Job 2: ContentsObject -> ContentsFile 업데이트 작업
    @Bean
    public Job contentsToFileJob() {
        return new JobBuilder("contentsToFileJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(contentsToFileStep(null, null))
                .build();
    }

    @Bean
    @JobScope
    public Step contentsToFileStep(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['folderId']}") Long folderId) {
        return new StepBuilder("contentsToFileStep", jobRepository)
                .tasklet(contentsToFileTasklet(type, folderId), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public ContentsToFileUpdateTasklet contentsToFileTasklet(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['folderId']}") Long folderId) {
        return switch (type) {
            case "anime" -> new ContentsToFileUpdateTasklet<>(animationFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId);
            case "movie" -> new ContentsToFileUpdateTasklet<>(movieFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId);
            case "drama" -> new ContentsToFileUpdateTasklet<>(dramaFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId);
            default -> new ContentsToFileUpdateTasklet<>(null, contentsObjectRepository, contentsFileRepository, type, folderId);
        };
    }

}
