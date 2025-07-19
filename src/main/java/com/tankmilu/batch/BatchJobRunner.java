package com.tankmilu.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchJobRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("실행할 Job 이름을 입력해주세요.");
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
            return;
        }

        String jobName = args[0];
        Job job = (Job) applicationContext.getBean(jobName);

        JobParametersBuilder builder = new JobParametersBuilder();
        builder.addString("run.id", String.valueOf(System.currentTimeMillis())); // 고유성을 위한 파라미터

        // job 이름 뒤에 오는 모든 'key=value' 인자들을 파싱하여 추가
        for (int i = 1; i < args.length; i++) {
            String[] keyValue = args[i].split("=");
            if (keyValue.length == 2) {
                builder.addString(keyValue[0], keyValue[1]);
            }
        }

        JobParameters jobParameters = builder.toJobParameters();

        System.out.println("Executing job " + jobName + " with parameters: " + jobParameters);
        JobExecution execution = jobLauncher.run(job, jobParameters);

        // Netty 때문에 배치작업은 종료코드 넣어줘야 함
        System.out.println("Job finished with status: " + execution.getStatus());
        System.exit(SpringApplication.exit(applicationContext));
    }
}
