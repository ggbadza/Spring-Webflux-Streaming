package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.FolderTreeEntity;
import com.tankmilu.webflux.enums.SubtitleEnum;
import com.tankmilu.webflux.record.DirectoryRecord;
import com.tankmilu.webflux.record.VideoFileRecord;
import com.tankmilu.webflux.repository.FolderTreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
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

    private final FFmpegService ffmpegService;

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

    public Mono<String> getFolderPath(Long folderId) {
        return folderTreeRepository.findByFolderId(folderId)
                .map(FolderTreeEntity::getFolderPath);
    }

    public Mono<String> checkSubtitle(String folderPath, List<String> subFiles) {
        return Flux.fromIterable(subFiles)
                .flatMap(subFile ->
                        Mono.fromCallable(() -> new File(folderPath + File.separator + subFile).exists())
                                .filter(exists -> exists)
                                .map(exists -> folderPath + File.separator + subFile)
                )
                .next() // 첫 번째로 발견된 요소만 전달
                .switchIfEmpty(Mono.empty()); // 값이 없으면 빈 Mono
    }

    public Mono<VideoFileRecord> getVideoFileInfo(Long folderId, String fileName) {
        return getFolderPath(folderId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("존재하지 않는 폴더 요청. folderId : " + folderId)))
                .flatMap(folderPath -> {
                    String filePath = folderPath + File.separator + fileName;
                    System.out.println(filePath);
                    // 자막 후보 경로 생성 및 체크 (병렬 처리 1)
                    List<String> subPaths = SubtitleEnum.generateSubtitleNames(fileName);
                    System.out.println(subPaths);
                    Mono<String> subtitleMono = checkSubtitle(folderPath, subPaths)
                            .defaultIfEmpty("-");

                    // 동영상 해상도 조회 (병렬 처리 2)
                    Mono<String> resolutionMono = Mono.fromCallable(() ->
                                    ffmpegService.getVideoMetaData(filePath)
                            )
                            .map(meta -> meta.get("width") + "x" + meta.get("height"))
                            .onErrorReturn("-")
                            .subscribeOn(Schedulers.boundedElastic())
                            .defaultIfEmpty("-");

                    // 두 결과 조합
                    return Mono.zip(subtitleMono, resolutionMono)
                            .map(tuple -> new VideoFileRecord(
                                    folderId,
                                    fileName,
                                    filePath,
                                    tuple.getT1(),  // subtitlePath
                                    tuple.getT2()   // resolution
                            ));
                });
    }


}
