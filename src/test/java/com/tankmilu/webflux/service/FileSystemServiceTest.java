package com.tankmilu.webflux.service;

import com.tankmilu.webflux.record.UserRegRequests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.List;

@SpringBootTest
public class FileSystemServiceTest {

    @Autowired
    private FileSystemService fileSystemService;

    @Test
    void fileSystemServiceTest_ExistsFiles() {
        List<String> list = fileSystemService.getFileList(2L).block();  // block()으로 결과 대기
        System.out.println(list);
    }

}
