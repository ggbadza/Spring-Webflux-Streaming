package com.tankmilu.webflux.service;

import com.tankmilu.webflux.record.DirectoryRecord;
import com.tankmilu.webflux.repository.FolderTreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileSystemService {

    private final FolderTreeRepository folderTreeRepository;

    public Mono<List<DirectoryRecord>> getFolderList(Long parentId) {
        return folderTreeRepository.findByParentFolderId(parentId)
                .map(folder -> new DirectoryRecord(folder.getFolderId(), folder.getName()))
                .collectList();
    }

    Mono<List<String>> getFileList(Long parentId) {
            return folderTreeRepository.findByFolderId(parentId)
                    .switchIfEmpty(
                            // 폴더가 없으면 즉시 에러 발생
                            Mono.error(new IllegalArgumentException("존재하지 않는 폴더 요청. pid : " + parentId))
                    )
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

    public Mono<List<DirectoryRecord>> getFolderAndFilesList(Long parentId) {
        Mono<List<DirectoryRecord>> foldersMono = getFolderList(parentId);

        Mono<List<DirectoryRecord>> filesMono = getFileList(parentId)
                .map(fileNames -> fileNames.stream()
                        .map(fileName -> new DirectoryRecord(null, fileName))
                        .collect(Collectors.toList()));

        // 두 목록을 결합하여 반환
        return Mono.zip(foldersMono, filesMono)
                .map(tuple -> {
                    List<DirectoryRecord> combinedList = new ArrayList<>();
                    combinedList.addAll(tuple.getT1()); // 폴더 목록
                    combinedList.addAll(tuple.getT2()); // 파일 목록
                    return combinedList;
                });
    }

    public Mono<String> getFilePath(Long folderId, String fileName) {
        return folderTreeRepository.findByFolderId(folderId)
                .map(folderTreeEntity -> folderTreeEntity.getFolderPath() + "/" + fileName);
    }


}
