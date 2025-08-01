package com.tankmilu.webflux.controller;

import com.tankmilu.webflux.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VideoControllerTest {

    @Autowired
    private VideoController videoController;

    @Autowired
    private VideoService videoService;

    @Test
    void getVideo() {
        StepVerifier.create(videoController.test())
                .expectNext("test")
                .verifyComplete();
    }

    @Test
    void getVideoWithFileNameAndRange() {
        String rangeHeader1 = "bytes=0-10000000"; // 10MB
        String rangeHeader2 = "bytes=0-"; 
        String rangeHeader3 = "bytes=0-100";
        String rangeHeader4 = "bytes=1050000-2000000";
        String rangeHeader5 = "bytes=2000000-1000000";
        String videoName = "video.mp4";

//        // 1번 헤더 테스트
//        Mono<ResponseEntity<Mono<DataBuffer>>> response1 = videoController.getVideoRange(videoName, null, rangeHeader1);
//        StepVerifier.create(response1)
//                .assertNext(entity -> {
//                    assertNotNull(entity);
//                    assertEquals(HttpStatus.PARTIAL_CONTENT, entity.getStatusCode());
//                    assertTrue(entity.getHeaders().getContentType().toString().startsWith("video"));
//                    assertTrue(entity.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE));
//                    assertEquals("bytes 0-1048575/320078896", entity.getHeaders().get(HttpHeaders.CONTENT_RANGE).get(0));
//                })
//                .verifyComplete();
//
//        // 2번 헤더 테스트
//        Mono<ResponseEntity<Mono<DataBuffer>>> response2 = videoController.getVideoRange(videoName, null, rangeHeader2);
//        StepVerifier.create(response2)
//                .assertNext(entity -> {
//                    assertNotNull(entity);
//                    assertEquals(HttpStatus.PARTIAL_CONTENT, entity.getStatusCode());
//                    assertTrue(entity.getHeaders().getContentType().toString().startsWith("video"));
//                    assertTrue(entity.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE));
//                    assertEquals("bytes 0-1048575/320078896", entity.getHeaders().get(HttpHeaders.CONTENT_RANGE).get(0));
//                })
//                .verifyComplete();
//
//        // 3번 헤더 테스트
//        Mono<ResponseEntity<Mono<DataBuffer>>> response3 = videoController.getVideoRange(videoName, rangeHeader3, null);
//        StepVerifier.create(response3)
//                .assertNext(entity -> {
//                    assertNotNull(entity);
//                    assertEquals(HttpStatus.PARTIAL_CONTENT, entity.getStatusCode());
//                    assertTrue(entity.getHeaders().getContentType().toString().startsWith("video"));
//                    assertTrue(entity.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE));
//                    assertEquals("bytes 0-100/320078896", entity.getHeaders().get(HttpHeaders.CONTENT_RANGE).get(0));
//                })
//                .verifyComplete();
//
//        // 4번 헤더 테스트
//        Mono<ResponseEntity<Mono<DataBuffer>>> response4 = videoController.getVideoRange(videoName, null, rangeHeader4);
//        StepVerifier.create(response4)
//                .assertNext(entity -> {
//                    assertNotNull(entity);
//                    assertEquals(HttpStatus.PARTIAL_CONTENT, entity.getStatusCode());
//                    assertTrue(entity.getHeaders().getContentType().toString().startsWith("video"));
//                    assertTrue(entity.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE));
//                    assertEquals("bytes 1050000-2000000/320078896", entity.getHeaders().get(HttpHeaders.CONTENT_RANGE).get(0));
//                })
//                .verifyComplete();
//
//        // 5번 헤더 테스트
//        try {
//            Mono<ResponseEntity<Mono<DataBuffer>>> response5 = videoController.getVideoRange(videoName,null, rangeHeader5);
//            StepVerifier.create(response5)
//                    .assertNext(entity -> {
//                        assertNotNull(entity);
//                        assertEquals(HttpStatus.PARTIAL_CONTENT, entity.getStatusCode());
//                        assertTrue(entity.getHeaders().getContentType().toString().startsWith("video"));
//                        assertTrue(entity.getHeaders().containsKey(HttpHeaders.CONTENT_RANGE));
//                        assertEquals("bytes 1050000-2000000/320078896", entity.getHeaders().get(HttpHeaders.CONTENT_RANGE).get(0));
//                    })
//                    .verifyComplete();
//        } catch (Exception e) {
//            assertInstanceOf(IllegalArgumentException.class, e);
//        }
    }
}