package com.tankmilu.batch.tasklet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tankmilu.batch.repository.folder.FolderTreeRepository;
import com.tankmilu.webflux.entity.folder.AnimationFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.DramaFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.entity.folder.MovieFolderTreeEntity;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@AllArgsConstructor
public class DirectoryProcessTasklet implements Tasklet {

    private final Path rootPath;
    private final FolderTreeRepository<?> repository;
    String type;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {

        // ExecutionContext에서 데이터 가져오기
        Map<Long, FolderTreeEntity> folderMap =
                (Map<Long, FolderTreeEntity>) chunkContext.getStepContext()
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

    private void processDirectory(Path rootPath, Map<Long, FolderTreeEntity> map) {
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
                    boolean isChanged = checkChanges(currentDir, rootPath, entity);
                    entity.setChangeCd(isChanged ? "Y" : "U"); // 변경되었을 경우 "Y", 변경사항 없을 시 "U"
                } else {
                    // DB에 없는 폴더 (예외 처리 또는 로깅)
                    System.err.println("Orphan folder detected: " + currentDir);
                }
            } else {
                // 3. _folder_info.json 생성 및 새 엔티티 추가
                Long newFolderId = nextKey++;
                writeFolderInfo(currentDir, newFolderId);

                FolderTreeEntity newEntity = createNewEntity(currentDir, rootPath, newFolderId, type);
                newEntity.setChangeCd("N"); // 새로 생성 할 경우 "N"
                map.put(newFolderId, newEntity);
            }

            // 4. 하위 디렉토리 BFS 탐색
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir, Files::isDirectory)) {
                for (Path subDir : stream) {
                    queue.add(subDir);
                }
            } catch (IOException e) {
                throw new RuntimeException("Directory access error: " + currentDir, e);
            }
        }
    }

    public long getNextKey(Map<Long, FolderTreeEntity> map) {
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
    private FolderTreeEntity createNewEntity(Path dir, Path rootDir, Long folderId, String type) {
        FolderTreeEntity newEntity = null;
        switch (type) {
            case "anime" -> newEntity = AnimationFolderTreeEntity.builder()
                    .folderId(folderId)
                    .name(dir.getFileName().toString())
                    .folderPath(dir.toString())
                    .parentFolderId(getParentFolderId(dir.getParent(), rootDir))
                    .subscriptionCode("default")
                    .hasFiles(false)
                    .build();
            case "drama" -> newEntity = DramaFolderTreeEntity.builder()
                    .folderId(folderId)
                    .name(dir.getFileName().toString())
                    .folderPath(dir.toString())
                    .parentFolderId(getParentFolderId(dir.getParent(), rootDir))
                    .subscriptionCode("default")
                    .hasFiles(false)
                    .build();
            case "movie" -> newEntity = MovieFolderTreeEntity.builder()
                    .folderId(folderId)
                    .name(dir.getFileName().toString())
                    .folderPath(dir.toString())
                    .parentFolderId(getParentFolderId(dir.getParent(), rootDir))
                    .subscriptionCode("default")
                    .hasFiles(false)
                    .build();
        }
        return newEntity;
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
}