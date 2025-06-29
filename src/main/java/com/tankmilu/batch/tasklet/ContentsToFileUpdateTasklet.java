package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.ContentsFileEntity;
import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.enums.SubtitleExtensionEnum;
import com.tankmilu.webflux.enums.VideoExtensionEnum;
import com.tankmilu.webflux.repository.ContentsFileRepository;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import com.tankmilu.webflux.service.FFmpegServiceProcessImpl;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Getter
public class ContentsToFileUpdateTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final FolderTreeRepository<T> folderRepository;
    private final ContentsObjectRepository contentsRepository;
    private final ContentsFileRepository fileRepository;
    private final String type;
    private final Long folderId;

    private final FFmpegServiceProcessImpl fFmpegServiceProcess;
    
    // 배치 결과를 저장할 리스트 생성
    private final List<ContentsFileEntity> filesToInsert = new ArrayList<>();
    private final List<ContentsFileEntity> filesToUpdate = new ArrayList<>();
    private final List<ContentsFileEntity> filesToDelete = new ArrayList<>();
    private final List<ContentsObjectEntity> contentsToUpdate = new ArrayList<>();


    @Value("${custom.batch.subtitle_folder}")
    private String tempSubtitleFolder;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("ContentsToFileUpdateTasklet 시작");

        // 1. 콘텐츠오브젝트 불러오기
        List<ContentsObjectEntity> contentsList;
        if (folderId==null || folderId==0) {
            // 1-1. 모든 ContentsObjectEntity 로드
            contentsList = contentsRepository.findAll()
                    .collectList()
                    .block();
        } else {
            // 1-2. folderId의 하위 폴더만 로드
            contentsList = contentsRepository.findContentsObjectEntitiesByTypeAndFolderIdRecursive(type, folderId)
                    .collectList()
                    .block();
        }

        if (contentsList == null || contentsList.isEmpty()) {
            log.info("콘텐츠 객체가 없습니다.");
            return RepeatStatus.FINISHED;
        }

        log.info("로드된 콘텐츠 객체 수: {}", contentsList.size());

        // 2-a. 모든 콘텐츠파일 로드 (콘텐츠 ID로 그룹화)
        List<ContentsFileEntity> allFiles = fileRepository.findAll()
                .collectList()
                .block();

        Long maxFileId = fileRepository.findMaxFileId()
                .defaultIfEmpty(0L) // Mono가 empty()로 완료되면 0L을 반환
                .block();

        Map<Long, List<ContentsFileEntity>> fileEntityByContentsId = new HashMap<>();
        if (allFiles != null) {
            for (ContentsFileEntity file : allFiles) {
                if (file.getContentsId() != null) {
                    fileEntityByContentsId.computeIfAbsent(file.getContentsId(), k -> new ArrayList<>())
                            .add(file);
                }
            }
        }

        log.info("기존 파일 엔티티 수: {}", allFiles != null ? allFiles.size() : 0);

        // 2-b. 실제 타입에 대한 폴더 객체 전체 로드
        List<T> folderList = folderRepository.findAll().collectList().block();

        // 폴더리스트를 HashMap으로 변환
        Map<Long, String> folderPathMap = Objects.requireNonNull(folderList, "폴더 목록은 null일 수 없습니다.")
                .stream()
                .collect(Collectors.toMap(
                        T::getFolderId,
                        T::getFolderPath,
                        (oldValue, newValue) -> oldValue // 중복 키 처리: 기존 값 유지
                ));


        // 3. 각 콘텐츠오브젝트에 대해 파일 검색 및 콘텐츠파일엔티티 업데이트
        /*
            1) 동영상 파일을 전부 검사
            2) 만약 동영상 파일이 존재한다면 자막 파일의 존재를 검사(자막은 동영상과 동일한 이름에 확장자만 자막 확장자)
            2-2) 자막이 ass가 아닌 경우 ass로 변환 수행
            3) existingFiles을 이용해서 동일한 파일이 존재시 무시하고,
            existingFiles에는 있지만 새로 검사한 파일중에 없다면 삭제 처리하고,
            existingFiles에는 없지만 새로 검사한 파일이 있다면 인설트 하고(자막 경로와 같이)
            existingFiles에 있고, 새로 검사한 파일 목록에도 있지만 자막 여부가 달라졌다면 업데이트
         */

        for (ContentsObjectEntity content : contentsList) {
            // 3-1. 폴더 경로 찾기
            String folderPath = folderPathMap.get(content.getFolderId());
            if (folderPath == null || folderPath.isEmpty()) {
                log.warn("콘텐츠 ID: {}에 대한 폴더 경로를 찾을 수 없습니다.", content.getContentsId());
                continue;
            }

            // 3-2. 폴더 내 파일 검색
            Path dirPath = Paths.get(folderPath);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                log.warn("경로가 존재하지 않거나 디렉토리가 아닙니다: {}", folderPath);
                continue;
            }


            // 3-3. 해당 콘텐츠 오브젝트의 DB에 저장된 파일들을 Map으로 변환
            // existingFiles : 기존 DB에 있던 파일 목록
            Map<String, ContentsFileEntity> existingFiles = fileEntityByContentsId // key : {확장자 없는 파일 명}, value : [{엔티티 객체}]
                    .getOrDefault(content.getContentsId(), new ArrayList<>())
                    .stream()
                    .collect(Collectors.toMap(
                            file -> getFileNameWithoutExtension(file.getFilePath()),
                            file -> file,
                            // 만약 같은 키가 있다면, 기존 값을 유지 (충돌 해결)
                            (existing, replacement) -> existing
                    ));


            // 3-4. 실제 콘텐츠 오브젝트의 폴더에서 비디오 파일만 조회하여 맵핑
            // filteredFiles : 현재 폴더에 존재하는 실제 파일 목록
            Map<String, String[]> filteredFiles; // key : {확장자 없는 파일 명}, value : [{비디오 파일 상대 경로},{자막 파일 상대 경로}]
            try (Stream<Path> videoStream = Files.list(dirPath)) {
                filteredFiles = videoStream
                        .filter(Files::isRegularFile)
                        .filter(path -> VideoExtensionEnum.isVideo(path.getFileName().toString()))
                        .collect(Collectors.toMap(
                                path -> getFileNameWithoutExtension(path.getFileName().toString()),  // 확장자 제거한 파일명을 key로
                                path -> new String[] {path.getFileName().toString(), ""}  // [파일전체이름, 자막(null)] 배열을 value로
                        ));

                // 3-5.자막 파일을 비디오 파일에 맵핑하여 처리
                try (Stream<Path> subtitleStream = Files.list(dirPath)) {
                    subtitleStream
                            .filter(Files::isRegularFile)
                            .filter(path -> SubtitleExtensionEnum.isSubtitle(path.getFileName().toString()))
                            .forEach(path -> {
                                String subtitleFileName = path.getFileName().toString();
                                String subtitleWithoutExt = getFileNameWithoutExtension(subtitleFileName);

                                // (확장자를 제거한)동일한 이름의 비디오 파일이 있는지 확인
                                if (filteredFiles.containsKey(subtitleWithoutExt)) {
                                    // 자막 파일명을 맵의 두 번째 배열 요소에 설정
                                    filteredFiles.get(subtitleWithoutExt)[1] = subtitleFileName;
                                }
                            });
                } catch (IOException e) {
                    log.error("자막 파일 처리 중 오류 발생: {}", dirPath, e);
                    continue;
                }
            } catch (IOException e) {
                log.error("디렉토리 처리 중 오류 발생: {}", dirPath, e);
                continue;
            }

            // 자막 업데이트 할 때 사용할 리스트 생성
            List<ContentsFileEntity> InsertOrUpdateFiles = new ArrayList<>();

            // existingFiles : 기존 DB, filteredFiles: 현재 파일
            // 4. 기존 엔티티 객체를 이용하여 현재 상태를 조회하여 엔티티 수정 혹은 삭제 리스트에 추가
            for (Map.Entry<String,ContentsFileEntity> existingEntry : existingFiles.entrySet()){
                // 4-1. 기존 파일 경로를 현재 콘텐츠의 파일 경로 목록과 비교하여 기존 파일이 삭제 리스트에 추가
                if (!filteredFiles.containsKey(existingEntry.getKey())) {
                    filesToDelete.add(existingEntry.getValue());
                // 4-2.자막 파일 경로가 변동 시(혹은 추가, 삭제 시) 업데이트
                } else {
                    boolean isUpdated = false;

                    // 파일 경로가 변경된 경우
                    if (!filteredFiles.get(existingEntry.getKey())[0].equals(existingEntry.getValue().getFilePath())) {
                        existingEntry.getValue().setFilePath(filteredFiles.get(existingEntry.getKey())[0]);
                        existingEntry.getValue().setNewRecord(false);
                        isUpdated = true;
                    }

                    // 자막 경로가 변경된 경우
                    if (!filteredFiles.get(existingEntry.getKey())[1].equals(existingEntry.getValue().getSubtitlePath())) {
                        existingEntry.getValue().setSubtitlePath(filteredFiles.get(existingEntry.getKey())[1]);
                        existingEntry.getValue().setSubtitleCreatedAtNow();
                        existingEntry.getValue().setNewRecord(false);
                        isUpdated = true;
                    }


                    // 파일 경로나 자막 경로 중 하나라도 변경된 경우 업데이트 리스트에 추가
                    if (isUpdated) {
                        filesToUpdate.add(existingEntry.getValue());
                        InsertOrUpdateFiles.add(existingEntry.getValue());
                    }
                }
            }
            boolean isContentsUpdated = false;
            // 4-3. filteredFiles 를 이용해서
            // existingFilePaths세트를 조회해서 파일이 추가 된 경우
            // 파일 엔티티 생성 후 인서트
            for (Map.Entry<String, String[]> entry : filteredFiles.entrySet()) {
                String fileNameWithoutExt = entry.getKey();
                String[] fileDTO = entry.getValue();

                // existingFiles에서 해당 파일명이 있는지 확인
                // 파일이 존재하지 않으면 새로운 엔티티를 생성하여 insert 리스트에 추가
                if (!existingFiles.containsKey(fileNameWithoutExt)) {
                    ContentsFileEntity newFile = ContentsFileEntity
                            .builder()
                            .fileId(maxFileId+1)
                            .contentsId(content.getId())
                            .fileName(fileNameWithoutExt)
                            .filePath(fileDTO[0])
                            .subtitlePath(fileDTO[1])
                            .build();
                    isContentsUpdated = true;

                    // 자막 존재 시 자막 생성일자 세팅
                    if (fileDTO[1]!=null){
                        newFile.setSubtitleCreatedAtNow();
                    }
                    filesToInsert.add(newFile);
                    InsertOrUpdateFiles.add(newFile);
                }
            }
            if (isContentsUpdated){
                content.setNewRecord(false);
                content.setModifiedAt(LocalDateTime.now());
                contentsToUpdate.add(content);
            }



            for(ContentsFileEntity entity: InsertOrUpdateFiles) {
                // 자막이 ass가 아닌 경우 자막 임시 폴더 검사 후 미존재시 ass 변환 수행
                if (entity.getSubtitlePath() != null && !entity.getSubtitlePath().isEmpty()) { // 실제 자막 파일이 있는 경우에만
                    if (!SubtitleExtensionEnum.ASS.isSupportedFile(folderPath + '/' + entity.getSubtitlePath())) { // 해당 자막이 ASS가 아닌 경우에만
                        log.info("ID : {}, FileName : {}, subtitlePath : {}", entity.getContentsId(), entity.getFileName(), entity.getSubtitlePath());
                        String outputPath = tempSubtitleFolder + '/' + entity.getFileId() + ".f.ass";
                        // outputPath에 실제 파일이 존재하는지 체크
                        File outputFile = new File(outputPath);
                        if (!outputFile.exists()) { // 이미 파일 존재하는지 체크
                            log.info("ass로 자막 변환 수행. file ID: {}", entity.getFileId());
                            String originalSubtitlePath = folderPath + '/' + entity.getSubtitlePath();
                            File originalSubtitleFile = new File(originalSubtitlePath);
                            String detectedEncoding = null;

                            // 자막 파일의 인코딩 체크
                            byte[] buf = new byte[65536];
                            try (FileInputStream fis = new FileInputStream(originalSubtitleFile)) {
                                UniversalDetector detector = new UniversalDetector(null);
                                int nread;
                                while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                                    detector.handleData(buf, 0, nread);
                                }
                                detector.dataEnd();
                                detectedEncoding = detector.getDetectedCharset();
                                detector.reset(); // 다음 사용을 위해 리셋
                                if (detectedEncoding == null) {
                                    log.error("자막 파일의 인코딩 체크 실패. 'CP949'로 설정 : {}", originalSubtitlePath);
                                    detectedEncoding = "CP949";
                                } else if (detectedEncoding.startsWith("UTF")) {
                                    detectedEncoding = "UTF-8";
                                }
                            } catch (IOException e) {
                                log.error("자막 파일의 인코딩 체크 실패. 'CP949'로 설정 : {}. Error: {}", originalSubtitlePath, e.getMessage());
                                detectedEncoding = "CP949";
                            }

                            fFmpegServiceProcess.convertSubtitleToAss(originalSubtitlePath, outputPath, "맑은 고딕", 20, detectedEncoding).block();
                        } else {
                            log.info("ass 자막 파일이 이미 존재합니다. : {}", outputPath);
                        }
                    }
                }
            }
        }
        
        log.info("파일 처리 결과: 추가 {}, 수정 {}, 삭제 {}", 
                filesToInsert.size(), filesToUpdate.size(), filesToDelete.size());
                
        // 각 리스트를 ExecutionContext에 직접 저장
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("filesToInsert", filesToInsert);
        
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("filesToUpdate", filesToUpdate);
        
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("filesToDelete", filesToDelete);

        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("contentsToUpdate", contentsToUpdate);
                
        // 실제 저장은 별도의 Tasklet에서 처리하도록 변경
        return RepeatStatus.FINISHED;
    }

    private String getFolderPath(ContentsObjectEntity content) {
        if (content.getFolderId() == null) {
            return null;
        }
        T folderEntity = folderRepository.findByFolderId(content.getFolderId()).block();

        return Objects.requireNonNull(folderEntity).getFolderPath();
    }

    // 확장자 제거해주는 메소드
    private String getFileNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName; // 확장자가 없는 경우 원래 파일명 반환
    }
}
