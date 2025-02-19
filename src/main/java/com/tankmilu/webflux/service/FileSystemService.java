package com.tankmilu.webflux.service;

import com.tankmilu.webflux.repository.FolderTreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileSystemService {

    private final FolderTreeRepository folderTreeRepository;

    Mono<List<HashMap<Long,String>>> getFolderList(Long parentId) {
        return folderTreeRepository.findByParentFolderId(parentId)
                .map(folder -> {
                    HashMap<Long, String> map = new HashMap<>();
                    map.put(folder.getFolderId(), folder.getName());
                    return map;
                })
                .collectList();
    }

    Mono<List<String>> getFileList(Long parentId) {
        return folderTreeRepository.findByFolderId(parentId)
                .map(folder -> {
                    File directory = new File(folder.getFolderPath());
                    File[] fileArray = directory.listFiles(); // File 객체 배열 반환
                    if (fileArray == null) {
                        return Collections.<String>emptyList();
                    }
                    return Arrays.stream(fileArray)
                            .filter(File::isFile)      // 파일만 필터링 (폴더 제외)
                            .map(File::getName)        // 파일 이름 추출
                            .collect(Collectors.toList());
                })
                // 동기 작업이므로 boundedElastic 스케줄러 사용
                .subscribeOn(Schedulers.boundedElastic());
    }

    Mono<String> getFilePath(Long folderId, String fileName) {
        return folderTreeRepository.findByFolderId(folderId)
                .map(folderTreeEntity -> folderTreeEntity.getFolderPath() + "/" + fileName);
    }

}
