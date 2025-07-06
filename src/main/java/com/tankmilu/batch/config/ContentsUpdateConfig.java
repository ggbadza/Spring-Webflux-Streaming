package com.tankmilu.batch.config;

import com.tankmilu.batch.tasklet.ContentsFileSaveTasklet;
import com.tankmilu.batch.tasklet.ContentsToFileUpdateTasklet;
import com.tankmilu.batch.tasklet.FolderToContentsUpdateTasklet;
import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.es.repository.ContentsObjectDocumentRepository;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import com.tankmilu.webflux.repository.ContentsKeywordsRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.folder.AnimationFolderTreeRepository;
import com.tankmilu.webflux.repository.folder.DramaFolderTreeRepository;
import com.tankmilu.webflux.repository.folder.MovieFolderTreeRepository;
import com.tankmilu.webflux.service.FFmpegService;
import com.tankmilu.webflux.service.FFmpegServiceProcessImpl;
import com.tankmilu.webflux.service.VideoService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ContentsUpdateConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionalOperator transactionalOperator;

    // 레파지토리 주입
    private final AnimationFolderTreeRepository animationFolderTreeRepository;
    private final MovieFolderTreeRepository movieFolderTreeRepository;
    private final DramaFolderTreeRepository dramaFolderTreeRepository;
    private final ContentsObjectRepository contentsObjectRepository;
    private final ContentsFileRepository contentsFileRepository;
    private final ContentsKeywordsRepository contentsKeywordsRepository;
    private final ContentsObjectDocumentRepository contentsObjectDocumentRepository;

    private final FFmpegServiceProcessImpl fFmpegService;
    private final VideoService videoService;

    // Job 1: FolderTree -> ContentsObject 업데이트 작업
    @Bean
    public Job folderToContentsJob() {
        return new JobBuilder("folderToContentsJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(folderToContentsStep(null))
                .build();
    }

    // Step 1 : 폴더 -> 컨텐츠 업데이트 작업
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
                    animationFolderTreeRepository, contentsObjectRepository, contentsKeywordsRepository,contentsObjectDocumentRepository,"anime", transactionalOperator);
            case "movie" -> new FolderToContentsUpdateTasklet<>(
                    movieFolderTreeRepository, contentsObjectRepository,contentsKeywordsRepository,contentsObjectDocumentRepository,"movie", transactionalOperator);
            case "drama" -> new FolderToContentsUpdateTasklet<>(
                    dramaFolderTreeRepository, contentsObjectRepository,contentsKeywordsRepository,contentsObjectDocumentRepository,"drama", transactionalOperator);
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    // Job 2: ContentsObject -> ContentsFile 업데이트 작업
    @Bean
    public Job contentsToFileJob() {
        return new JobBuilder("contentsToFileJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(contentsToFileUpdateStep(null, null))
                .next(contentsFileSaveStep()) // 파일 저장 단계 추가 - 트랜잭션 처리
                .build();
    }

    // Step 1: 컨텐츠의 파일 업데이트 작업
    @Bean
    @JobScope
    public Step contentsToFileUpdateStep(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['folderId']}") Long folderId) {
        return new StepBuilder("contentsToFileUpdateStep", jobRepository)
                .tasklet(contentsToFileTasklet(type, folderId), transactionManager)
                .build();
    }


    // Step 2: DB 저장 단계
    @Bean
    @JobScope
    public Step contentsFileSaveStep() {
        return new StepBuilder("contentsFileSaveStep", jobRepository)
                .tasklet(contentsFileSaveTasklet(), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public ContentsToFileUpdateTasklet<?> contentsToFileTasklet(
            @Value("#{jobParameters['type']}") String type,
            @Value("#{jobParameters['folderId']}") Long folderId) {
        return switch (type) {
            case "anime" -> new ContentsToFileUpdateTasklet<>(animationFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId, fFmpegService, videoService);
            case "movie" -> new ContentsToFileUpdateTasklet<>(movieFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId, fFmpegService, videoService);
            case "drama" -> new ContentsToFileUpdateTasklet<>(dramaFolderTreeRepository, contentsObjectRepository, contentsFileRepository, type, folderId, fFmpegService, videoService);
            default -> throw new IllegalArgumentException("컨텐츠 타입 에러: " + type + " 타입이 다음 타입과 같은지 확인해주세요. 'anime', 'movie', 'drama'.");
        };
    }


    @Bean
    @StepScope
    public ContentsFileSaveTasklet contentsFileSaveTasklet() {
        return new ContentsFileSaveTasklet(contentsFileRepository, contentsObjectRepository, transactionalOperator);
    }
}