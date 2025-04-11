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
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@AllArgsConstructor
public class DirectoryProcessTasklet<T extends FolderTreeEntity> implements Tasklet {

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
                Long folderId = readFolderIdFromJson(infoFile);

                // 2. map에 folder_id 존재 시 데이터 비교
                if (map.containsKey(folderId)) {
                    FolderTreeEntity entity = map.get(folderId);

                    entity.setChangeCd(checkChanges(currentDir, rootPath, entity) ? "Y" : "U"); // 변경되었을 경우 "Y", 변경사항 없을 시 "U"
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
        return Collections.max(map.keySet())+1;
    }

    // _folder_info.json 쓰기
    private void writeFolderInfo(Path dir, Long folderId) {
        try {
            ObjectNode jsonNode = new ObjectMapper().createObjectNode();
            jsonNode.put("folder_id", folderId);
            new ObjectMapper().writeValue(dir.resolve("_folder_info.json").toFile(), jsonNode);
        } catch (IOException e) {
            throw new RuntimeException(dir.toString()+" 경로에 _folder_info.json 파일 쓰기를 실패했습니다. ", e);
        }
    }

    // Entity 생성 메소드
    private T createNewEntity(Path dir, Path rootDir, long folderId) {
        return entityBuilder.build(
                folderId,
                dir.getFileName().toString(),
                dir.toString(),
                getParentFolderId(dir.getParent(), rootDir),
                "100",
                LocalDateTime.now(),
                LocalDateTime.now(),
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
                    if (!fileName.equals(".folder_info.json")
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

    // Entity 객체와 디렉토리 상태 비교
    private boolean checkChanges(Path dir, Path rootDir, FolderTreeEntity entity) {
        // 이름, 경로, 부모ID 비교
        String currentName = dir.getFileName().toString();
        String currentPath = dir.toString();
        Long currentParentId = getParentFolderId(dir.getParent(), rootDir);

        return !currentName.equals(entity.getName()) ||
                !currentPath.equals(entity.getFolderPath()) ||
                !Objects.equals(currentParentId, entity.getParentFolderId());
    }

    // 상위 폴더 Id 받는 메소드
    private Long getParentFolderId(Path parentDir, Path rootDir) {
        if (parentDir == null || !parentDir.startsWith(rootDir)) return null;
        Path parentInfo = parentDir.resolve(".folder_info.json");
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