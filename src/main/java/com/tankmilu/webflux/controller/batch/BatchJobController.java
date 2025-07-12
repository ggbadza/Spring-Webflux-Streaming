package com.tankmilu.webflux.controller.batch;

import com.tankmilu.batch.config.FolderSyncConfig;
import com.tankmilu.webflux.record.ContentsToFileBatchRequest;
import com.tankmilu.webflux.record.FolderSyncBatchRequest;
import com.tankmilu.webflux.record.FolderToContentsBatchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 배치 작업을 실행하고 모니터링하기 위한 REST API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("${app.batch.urls.base}")
@RequiredArgsConstructor
public class BatchJobController {
    
    private final JobLauncher jobLauncher;

    private final Job folderSyncJob;

    private final Job folderToContentsJob;

    private final Job contentsToFileJob;

    private final JobExplorer jobExplorer;

    private final JobOperator jobOperator;
    
    /**
     * 폴더 동기화 배치 작업을 실행합니다.
     * 
     * @param folderSyncBatchRequest (type : 폴더 유형 (anime, movie, drama), directoryPath: 스캔할 디렉토리 경로)
     * @return 작업 실행 결과
     */
    @PostMapping("${app.batch.urls.folder_sync}")
    public Mono<ResponseEntity<Map<String, Object>>> runFolderSyncJob(
            @RequestBody FolderSyncBatchRequest folderSyncBatchRequest) {

        String type = folderSyncBatchRequest.type();
        String directoryPath = folderSyncBatchRequest.directoryPath();
        String deleteYn = folderSyncBatchRequest.deleteYn();
        
        log.info("폴더 동기화 배치 작업 요청 - 유형: {}, 경로: {}, 삭제 유무: {}", type, directoryPath, deleteYn);
        
        // 배치 작업은 블로킹 작업이므로 별도 스레드에서 실행
        return Mono.fromCallable(() -> {
            try {
                // 작업 파라미터 설정
                JobParameters params = new JobParametersBuilder()
                    .addString("type", type)
                    .addString("directoryPath", directoryPath)
                    .addString("deleteYn", deleteYn)
                    .addDate("time", new Date())  // 매번 고유한 파라미터를 위해 현재 시간 추가
                    .toJobParameters();
                
                // 배치 작업 실행
                JobExecution execution = jobLauncher.run(folderSyncJob, params);
                
                // 응답 생성
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", execution.getJobId());
                response.put("status", execution.getStatus().toString());
                response.put("startTime", execution.getStartTime());
                response.put("type", type);
                response.put("directoryPath", directoryPath);
                response.put("deleteYn", deleteYn);
                
                log.info("폴더 동기화 배치 작업 시작 - JobID: {}, 상태: {}", 
                        execution.getJobId(), execution.getStatus());
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("배치 작업 실행 중 오류 발생", e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                errorResponse.put("type", type);
                errorResponse.put("directoryPath", directoryPath);
                errorResponse.put("deleteYn", deleteYn);
                return ResponseEntity.internalServerError().body(errorResponse);
            }
        }).subscribeOn(Schedulers.boundedElastic());  // 블로킹 작업을 위한 별도 스케줄러 사용
    }
    
    /**
     * 폴더 정보를 콘텐츠 객체로 변환하는 배치 작업을 실행합니다.
     * 
     * @param folderToContentsBatchRequest 배치 요청 정보
     * @return 작업 실행 결과
     */
    @PostMapping("${app.batch.urls.folder_to_contents}")
    public Mono<ResponseEntity<Map<String, Object>>> runFolderToContentsJob(
            @RequestBody FolderToContentsBatchRequest folderToContentsBatchRequest) {

        String type = folderToContentsBatchRequest.type();
        
        log.info("폴더→콘텐츠 변환 배치 작업 요청 - 유형: {}", type);
        
        // 배치 작업은 블로킹 작업이므로 별도 스레드에서 실행
        return Mono.fromCallable(() -> {
            try {
                // 작업 파라미터 설정
                JobParameters params = new JobParametersBuilder()
                    .addString("type", type)
                    .addDate("time", new Date())  // 매번 고유한 파라미터를 위해 현재 시간 추가
                    .toJobParameters();
                
                // 배치 작업 실행
                JobExecution execution = jobLauncher.run(folderToContentsJob, params);
                
                // 응답 생성
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", execution.getJobId());
                response.put("status", execution.getStatus().toString());
                response.put("startTime", execution.getStartTime());
                response.put("type", type);
                
                log.info("폴더→콘텐츠 변환 배치 작업 시작 - JobID: {}, 상태: {}", 
                        execution.getJobId(), execution.getStatus());
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("배치 작업 실행 중 오류 발생", e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                errorResponse.put("type", type);
                return ResponseEntity.internalServerError().body(errorResponse);
            }
        }).subscribeOn(Schedulers.boundedElastic());  // 블로킹 작업을 위한 별도 스케줄러 사용
    }
    
    /**
     * 콘텐츠 객체에서 파일 정보를 추출하는 배치 작업을 실행합니다.
     * 
     * @param contentsToFileBatchRequest 배치 요청 정보
     * @return 작업 실행 결과
     */
    @PostMapping("${app.batch.urls.contents_to_file}")
    public Mono<ResponseEntity<Map<String, Object>>> runContentsToFileJob(
            @RequestBody ContentsToFileBatchRequest contentsToFileBatchRequest) {

        String type = contentsToFileBatchRequest.type();
        Long folderId = contentsToFileBatchRequest.folderId();
        
        log.info("콘텐츠→파일 변환 배치 작업 요청 - 유형: {}, 폴더ID: {}", type, folderId);
        
        // 배치 작업은 블로킹 작업이므로 별도 스레드에서 실행
        return Mono.fromCallable(() -> {
            try {
                // 작업 파라미터 설정
                JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                    .addString("type", type)
                    .addDate("time", new Date());  // 매번 고유한 파라미터를 위해 현재 시간 추가
                
                // folderId가 있으면 추가
                paramsBuilder.addString("folderId", folderId != null ? String.valueOf(folderId) : null);
                
                JobParameters params = paramsBuilder.toJobParameters();
                
                // 배치 작업 실행
                JobExecution execution = jobLauncher.run(contentsToFileJob, params);
                
                // 응답 생성
                Map<String, Object> response = new HashMap<>();
                response.put("jobId", execution.getJobId());
                response.put("status", execution.getStatus().toString());
                response.put("startTime", execution.getStartTime());
                response.put("type", type);
                response.put("folderId", folderId);
                
                log.info("콘텐츠→파일 변환 배치 작업 시작 - JobID: {}, 상태: {}", 
                        execution.getJobId(), execution.getStatus());
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("배치 작업 실행 중 오류 발생", e);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                errorResponse.put("type", type);
                errorResponse.put("folderId", folderId);
                return ResponseEntity.internalServerError().body(errorResponse);
            }
        }).subscribeOn(Schedulers.boundedElastic());  // 블로킹 작업을 위한 별도 스케줄러 사용
    }
    
    /**
     * 모든 폴더 동기화 배치 작업의 실행 내역을 조회
     * 
     * @return 배치 작업 실행 내역 목록
     */
    @GetMapping("${app.batch.urls.jobs}")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getJobList() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            
            // 폴더 동기화 작업 인스턴스 목록 조회
            List<JobInstance> jobInstances = jobExplorer.findJobInstancesByJobName(
                    "folderSyncJob", 0, 100); // 최근 100개 작업만 조회
            
            for (JobInstance jobInstance : jobInstances) {
                List<JobExecution> executions = jobExplorer.getJobExecutions(jobInstance);
                if (!executions.isEmpty()) {
                    // 가장 최근 실행 내역 사용
                    JobExecution jobExecution = executions.get(0);
                    
                    Map<String, Object> jobInfo = new HashMap<>();
                    jobInfo.put("jobId", jobExecution.getJobId());
                    jobInfo.put("jobName", jobInstance.getJobName());
                    jobInfo.put("status", jobExecution.getStatus().toString());
                    jobInfo.put("startTime", jobExecution.getStartTime());
                    jobInfo.put("endTime", jobExecution.getEndTime());
                    
                    // 작업 파라미터 추가
                    Map<String, Object> parameters = new HashMap<>();
                    for (String key : jobExecution.getJobParameters().getParameters().keySet()) {
                        parameters.put(key, jobExecution.getJobParameters().getParameters().get(key).getValue());
                    }
                    jobInfo.put("parameters", parameters);
                    
                    result.add(jobInfo);
                }
            }
            
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 특정 배치 작업의 상세 상태를 조회
     *
     * @param jobId 조회할 작업 ID
     * @return 작업 상태 정보
     */
    @GetMapping("${app.batch.urls.status}/{jobId}")
    public Mono<ResponseEntity<Map<String, Object>>> getJobStatus(@PathVariable Long jobId) {
        return Mono.fromCallable(() -> {
            JobExecution jobExecution = jobExplorer.getJobExecution(jobId);

            Map<String, Object> response = new HashMap<>();

            if (jobExecution == null) {
                log.info("배치 작업 ID 미존재 - JobID: {}", jobId);
                response.put("msg", "작업 ID에 대한 정보가 존재하지 않습니다..");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("jobId", jobExecution.getJobId());
            response.put("jobName", jobExecution.getJobInstance().getJobName());
            response.put("status", jobExecution.getStatus().toString());
            response.put("startTime", jobExecution.getStartTime());
            response.put("endTime", jobExecution.getEndTime());
            response.put("exitCode", jobExecution.getExitStatus().getExitCode());
            response.put("exitDescription", jobExecution.getExitStatus().getExitDescription());
            
            // 작업 파라미터 추가
            Map<String, Object> parameters = new HashMap<>();
            for (String key : jobExecution.getJobParameters().getParameters().keySet()) {
                parameters.put(key, jobExecution.getJobParameters().getParameters().get(key).getValue());
            }
            response.put("parameters", parameters);
            
            // 단계(Step) 실행 정보 추가
            List<Map<String, Object>> steps = new ArrayList<>();
            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                Map<String, Object> stepInfo = new HashMap<>();
                stepInfo.put("stepName", stepExecution.getStepName());
                stepInfo.put("status", stepExecution.getStatus().toString());
                stepInfo.put("readCount", stepExecution.getReadCount());
                stepInfo.put("writeCount", stepExecution.getWriteCount());
                stepInfo.put("commitCount", stepExecution.getCommitCount());
                stepInfo.put("filterCount", stepExecution.getFilterCount());
                stepInfo.put("readSkipCount", stepExecution.getReadSkipCount());
                stepInfo.put("writeSkipCount", stepExecution.getWriteSkipCount());
                stepInfo.put("processSkipCount", stepExecution.getProcessSkipCount());
                stepInfo.put("rollbackCount", stepExecution.getRollbackCount());
                steps.add(stepInfo);
            }
            response.put("steps", steps);
            
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 실행 중인 배치 작업을 중지
     * 
     * @param jobExecutionId 중지할 작업 실행 ID
     * @return 작업 중지 결과
     */
    @GetMapping("${app.batch.urls.stop}/{jobExecutionId}")
    public Mono<ResponseEntity<Map<String, Object>>> stopJob(@PathVariable Long jobExecutionId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            try {
                // 작업 중지 시도
                boolean stopped = jobOperator.stop(jobExecutionId);
                
                response.put("jobExecutionId", jobExecutionId);
                response.put("stopped", stopped);
                
                if (stopped) {
                    log.info("배치 작업 중지 성공 - JobExecutionID: {}", jobExecutionId);
                    return ResponseEntity.ok(response);
                } else {
                    log.warn("배치 작업 중지 실패 - JobExecutionID: {}", jobExecutionId);
                    response.put("msg", "작업을 중지할 수 없습니다. 이미 완료되었거나 존재하지 않는 작업일 수 있습니다.");
                    return ResponseEntity.badRequest().body(response);
                }
            } catch (Exception e) {
                log.error("배치 작업 중지 중 오류 발생", e);
                response.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(response);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 배치 작업을 재시작하는 메소드
     * 
     * @param jobExecutionId 다시 시작할 작업 실행 ID
     * @return 작업 재시작 결과
     */
    @GetMapping("${app.batch.urls.restart}/{jobExecutionId}")
    public Mono<ResponseEntity<Map<String, Object>>> restartJob(@PathVariable Long jobExecutionId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> response = new HashMap<>();
            
            try {
                // 실패한 작업 재시작
                Long restartedJobExecutionId = jobOperator.restart(jobExecutionId);
                
                response.put("originalJobExecutionId", jobExecutionId);
                response.put("restartedJobExecutionId", restartedJobExecutionId);
                response.put("success", true);
                
                log.info("배치 작업 재시작 성공 - 원본 JobExecutionID: {}, 재시작 JobExecutionID: {}", 
                        jobExecutionId, restartedJobExecutionId);
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("배치 작업 재시작 중 오류 발생", e);
                response.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(response);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
