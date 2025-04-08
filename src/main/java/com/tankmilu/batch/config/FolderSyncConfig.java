package com.tankmilu.batch.config;

import com.tankmilu.batch.repository.folder.AnimationFolderTreeRepository;
import com.tankmilu.batch.repository.folder.DramaFolderTreeRepository;
import com.tankmilu.batch.repository.folder.FolderTreeRepository;
import com.tankmilu.batch.repository.folder.MovieFolderTreeRepository;
import com.tankmilu.batch.tasklet.DataLoadTasklet;
import com.tankmilu.batch.tasklet.DbUpdateTasklet;
import com.tankmilu.batch.tasklet.DirectoryProcessTasklet;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
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
                .start(dataLoadStep(null, null))     // Step1: 데이터 로드
                .next(directoryProcessStep(null))    // Step2: 디렉토리 처리
                .next(dbUpdateStep())               // Step3: DB 업데이트
                .build();
    }

    // Step 1 : DB에서 데이터 로드
    @Bean
    @JobScope
    public Step dataLoadStep(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['directoryPath']}") String path) {

        return new StepBuilder("dataLoadStep", jobRepository)
                .tasklet(dataLoadTasklet(type), transactionManager)
                .build();
    }


    // Step 2 : 디렉토리 검색
    @Bean
    @JobScope
    public Step directoryProcessStep(
            @Value("#{jobParameters['directoryPath']}") String path) {

        return new StepBuilder("directoryProcessStep", jobRepository)
                .tasklet(directoryProcessTasklet(), transactionManager)
                .build();
    }

    // Step 3 : DB 업데이트
    @Bean
    public Step dbUpdateStep() {
        return new StepBuilder("dbUpdateStep", jobRepository)
                .tasklet(dbUpdateTasklet(), transactionManager)
                .build();
    }

    /*
    Tasklet 정의
     */

    @Bean
    @StepScope
    public DataLoadTasklet dataLoadTasklet(
            @Value("#{jobParameters['type']}") String type) {

        return new DataLoadTasklet(getRepositoryByType(type));
    }

    @Bean
    @StepScope
    public DirectoryProcessTasklet directoryProcessTasklet(
            @Value("#{jobParameters['directoryPath']}") String path,
            @Value("#{jobParameters['type']}") String type) {

        return new DirectoryProcessTasklet(
                Path.of(path),
                getRepositoryByType(type)
        );
    }

    @Bean
    @StepScope
    public DbUpdateTasklet dbUpdateTasklet(
            @Value("#{jobParameters['type']}") String type) {

        return new DbUpdateTasklet(getRepositoryByType(type));
    }

    // 타입별 레파지토리 선택
    private FolderTreeRepository<? extends FolderTreeEntity> getRepositoryByType(String type) {
        return switch (type.toLowerCase()) {
            case "ani" -> animationFolderTreeRepository;
            case "movie" -> movieFolderTreeRepository;
            case "drama" -> dramaFolderTreeRepository;
            default -> throw new IllegalArgumentException("존재하지 않는 타입 입니다 : " + type);
        };
    }

}
