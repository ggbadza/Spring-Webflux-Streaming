package com.tankmilu.batch.tasklet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.enums.VideoExtensionEnum;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@AllArgsConstructor
public class FolderDirectoryProcessTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final Path rootPath;
    private final EntityBuilder<T> entityBuilder;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // ExecutionContext에서 데이터 가져오기
        Map<Long, T> folderMap = (Map<Long, T>) chunkContext.getStepContext()
                        .getStepExecution()
                        .getJobExecution()
                        .getExecutionContext()
                        .get("folderMap");

        // 디렉토리 처리 로직 구현
        processDirectory(rootPath, folderMap);

        // ExecutionContext에 저장
        chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .put("folderMap", folderMap);

        return RepeatStatus.FINISHED;
    }

    private void processDirectory(Path rootPath, Map<Long, T> map) {
        long nextKey = getNextKey(map);
        Queue<Path> queue = new LinkedList<>();
        queue.add(rootPath);

        while (!queue.isEmpty()) {
            Path currentDir = queue.poll();
            Path infoFile = currentDir.resolve("_folder_info.json");

            // 1. _folder_info.json 존재 여부 확인
            if (Files.exists(infoFile)) {
                // 정보 파일 숨김 처리 (리눅스 기반 도커에서는 작동 안함)
//                try {ㅠ
//                    if (!(Boolean) Files.getAttribute(infoFile, "dos:hidden")) {
//                        Files.setAttribute(infoFile, "dos:hidden", true);
//                    }
//                } catch (Exception e) {
//                // 숨김 속성 설정 실패해도 계속 진행
//                    log.debug("숨김 속성 설정 실패 (무시됨): '{}', \nERROR : {}", infoFile,e.getMessage());
//                }


                Long folderId = readFolderIdFromJson(infoFile);

                // 2. map에 folder_id 존재 시 데이터 비교
                if (map.containsKey(folderId)) {
                    FolderTreeEntity entity = map.get(folderId);

                    updateEntityIfChanged(currentDir, rootPath, entity);
                } else {
                    // 3. DB에 없는 폴더의 경우 새로운 엔티티 생성
                    T newEntity = createNewEntity(currentDir, rootPath, folderId);
                    newEntity.setChangeCd("N"); // 새로 생성 할 경우 "N"
                    map.put(folderId, newEntity);
                }
            } else {
                // 4. _folder_info.json 생성 및 새 엔티티 추가
                Long newFolderId = nextKey++;
                writeFolderInfo(currentDir, newFolderId);

                T newEntity = createNewEntity(currentDir, rootPath, newFolderId);
                newEntity.setChangeCd("N"); // 새로 생성 할 경우 "N"
                map.put(newFolderId, newEntity);
            }

            // 5. 하위 디렉토리 BFS 탐색(폴더 넘버링 순서를 위해서)
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, Files::isDirectory)) {
                for (Path subDir : stream) {
                    queue.add(subDir);
                }
            } catch (IOException e) {
                throw new RuntimeException("디렉토리 탐색에 실패하였습니다. : " + currentDir, e);
            }
        }
    }

    public long getNextKey(Map<Long, T> map) {
        try {
            return Collections.max(map.keySet()) + 1;
        } catch (NoSuchElementException e) {
            return 1L;
        }

    }



    // _folder_info.json 쓰기
    private void writeFolderInfo(Path dir, Long folderId) {
        try {
            // 디렉토리가 존재하는지 확인하고, 존재하지 않으면 생성
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("디렉토리를 생성했습니다: {}", dir);
            }

            
            ObjectNode jsonNode = new ObjectMapper().createObjectNode();
            jsonNode.put("folder_id", folderId);

            // 파일 경로 변수 선언
            Path filePath = dir.resolve("_folder_info.json");

            new ObjectMapper().writeValue(filePath.toFile(), jsonNode);

            // 숨김 속성 설정 시도
            try {
                Files.setAttribute(filePath, "dos:hidden", true);
            } catch (Exception e) {
                // 숨김 속성 설정 실패해도 계속 진행
                log.debug("숨김 속성 설정 실패 (무시됨): {}", e.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(dir.toString()+" 경로에 _folder_info.json 파일 쓰기를 실패했습니다. ", e);
        }
    }

    // Entity 생성 메소드
    private T createNewEntity(Path dir, Path rootDir, long folderId) {
        LocalDateTime fileModifiedTime;
        try {
            BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
            FileTime lastModifiedTime = attrs.lastModifiedTime();
            fileModifiedTime = LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault());

        } catch (Exception e) {
            fileModifiedTime=LocalDateTime.now();
        }
        return entityBuilder.build(
                folderId,
                dir.getFileName().toString(),
                dir.toString(),
                getParentFolderId(dir.getParent(), rootDir),
                "100",
                LocalDateTime.now(),
                fileModifiedTime,
                hasFiles(dir)
        );
    }

    // 해당 경로에 비디오 파일이 존재하는지 체크
    private boolean hasFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString();
                    // 정보 파일이 아니면서 동영상 파일인 경우
                    if (!fileName.equals("_folder_info.json")
                            && VideoExtensionEnum.isVideo(fileName)) {
                        return true;
                    }
                }
            }
        } catch (IOException ignored) {
            // 예외 발생 시 false 반환
        }
        return false;
    }

    // Entity 객체와 디렉토리 상태 비교 및 업데이트
    private FolderTreeEntity updateEntityIfChanged(Path dir, Path rootDir, FolderTreeEntity entity) {
        // 경로, 부모ID 비교
        String currentPath = dir.toString();
        Long currentParentId = getParentFolderId(dir.getParent(), rootDir);
        long minutesDiff;
        
        // 파일 수정 시간 가져오기
        try {
            BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
            FileTime lastModifiedTime = attrs.lastModifiedTime();
            LocalDateTime fileModifiedTime = LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault());
            // 엔티티의 수정 시간
            LocalDateTime entityModifiedTime = entity.getModifiedAt();

            // 수정 시간 차이 계산 (분 단위)
            minutesDiff = Math.abs(
                    ChronoUnit.MINUTES.between(entityModifiedTime, fileModifiedTime));
        } catch (Exception e) {
            minutesDiff = 0;
        }

        // 경로가 변경된 경우
        if (!currentPath.equals(entity.getFolderPath())) {
            entity.setFolderPath(currentPath);
            entity.setChangeCd("Y");
            log.info(entity +"경로 변경 됨");
        }

        // 부모 ID가 변경된 경우
        if (!Objects.equals(currentParentId, entity.getParentFolderId())) {
            entity.setParentFolderId(currentParentId);
            entity.setChangeCd("Y");
            log.info(entity +"부모 ID 변경 됨");
        }
        // 수정 시간 차이가 1분 이상인 경우
        if (minutesDiff >= 1) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
                FileTime lastModifiedTime = attrs.lastModifiedTime();
                LocalDateTime fileModifiedTime = LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault());
                entity.setModifiedAt(fileModifiedTime);
                entity.setChangeCd("Y");
                log.info(entity.getModifiedAt() +"수정시간 변경 됨");
            } catch (Exception e) {
                log.info(entity +"수정시간 변경 됨, 에러 발생");
                // 예외 발생 시 무시
            }
        }
        
        // 파일 유무 변경 시
        boolean hasFile= hasFiles(dir);
        if(entity.getHasFiles()!=hasFile){
            entity.setHasFiles(hasFile);
            entity.setChangeCd("Y");
        }

        // 변경 여부와 관계없이 항상 엔티티 반환
        return entity;
    }

    // 상위 폴더 Id 받는 메소드
    private Long getParentFolderId(Path parentDir, Path rootDir) {
        if (parentDir == null || !parentDir.startsWith(rootDir)) return null;
        Path parentInfo = parentDir.resolve("_folder_info.json");
        return Files.exists(parentInfo) ? readFolderIdFromJson(parentInfo) : null;
    }

    private Long readFolderIdFromJson(Path file) {
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            return root.get("folder_id").asLong();
        } catch (IOException e) {
            throw new RuntimeException("JSON 읽기 실패", e);
        }
    }

    @FunctionalInterface
    public interface EntityBuilder<E extends FolderTreeEntity> {
        E build(Long folderId, String name, String folderPath, Long parentFolderId,
                String subscriptionCode, LocalDateTime createdAt, LocalDateTime modifiedAt, boolean hasFiles);
    }
}