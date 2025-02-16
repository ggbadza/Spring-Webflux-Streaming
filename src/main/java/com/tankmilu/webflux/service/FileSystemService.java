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
                .flatMap(folder -> Mono.fromCallable(() -> {
                    File directory = new File(folder.getFolderPath());
                    String[] files = directory.list();
                    if (files == null) {
                        return Collections.<String>emptyList();
                    }
                    return Arrays.asList(files);
                }))
                // 동기 작업이므로 boundedElastic 스케줄러 사용
                .subscribeOn(Schedulers.boundedElastic());
    }

    Mono<String> getFilePath(Long folderId, String fileName) {
        return folderTreeRepository.findByFolderId(folderId)
                .map(folderTreeEntity -> folderTreeEntity.getFolderPath() + "/" + fileName);
    }

}
