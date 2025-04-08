package com.tankmilu.batch.tasklet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class FolderSyncTasklet {
    private final String directoryPath;
    private final FolderTreeRepository<? extends FolderTreeEntity> repository;
    private final String type;

    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {

        // 1. DB 데이터 로드
        Map<Long, FolderTreeEntity> folderMap = new HashMap<>();
        long maxFolderId = loadDbData(folderMap);

        // 2. 디렉토리 스캔 및 처리
        AtomicLong currentMaxId = new AtomicLong(maxFolderId);
        processDirectories(Path.of(directoryPath), folderMap, currentMaxId);

        // 3. DB 업데이트
        updateDatabase(folderMap);

        return RepeatStatus.FINISHED;
    }

    private long loadDbData(Map<Long, FolderTreeEntity> folderMap) {
        List<? extends FolderTreeEntity> entities = repository.findAll().collectList().block();
        long maxId = entities.stream()
                .mapToLong(FolderTreeEntity::getFolderId)
                .max()
                .orElse(0L);
        entities.forEach(e -> folderMap.put(e.getFolderId(), e));
        return maxId;
    }

    private void processDirectories(Path rootDir, Map<Long, FolderTreeEntity> folderMap, AtomicLong currentMaxId) {
        try {
            Files.walk(rootDir)
                    .filter(Files::isDirectory)
                    .forEach(dir -> processDirectory(dir, rootDir, folderMap, currentMaxId));
        } catch (IOException e) {
            throw new RuntimeException("디렉토리 탐색 실패", e);
        }
    }

    private void processDirectory(Path dir, Path rootDir,
                                  Map<Long, FolderTreeEntity> folderMap, AtomicLong currentMaxId) {

        Path infoFile = dir.resolve(".folder_info.json");
        boolean isNew = false;
        FolderTreeEntity entity;

        if (Files.exists(infoFile)) {
            Long folderId = readFolderIdFromJson(infoFile);
            entity = folderMap.get(folderId);

            if (entity == null) {
                // DB에 없는 경우 새로 생성
                isNew = true;
                entity = createNewEntity(dir, rootDir, currentMaxId.incrementAndGet());
                writeFolderInfoJson(infoFile, entity.getFolderId());
            } else {
                // 변경 사항 체크
                boolean isChanged = checkChanges(dir, rootDir, entity);
                entity.setChangeCd(isChanged ? "C" : "U");
            }
        } else {
            // 신규 생성
            isNew = true;
            entity = createNewEntity(dir, rootDir, currentMaxId.incrementAndGet());
            writeFolderInfoJson(infoFile, entity.getFolderId());
        }

        if (isNew) {
            entity.setChangeCd("N");
            folderMap.put(entity.getFolderId(), entity);
        }
    }

    private boolean checkChanges(Path dir, Path rootDir, FolderTreeEntity entity) {
        // 이름, 경로, 부모ID 비교
        String currentName = dir.getFileName().toString();
        String currentPath = dir.toString();
        Long currentParentId = getParentFolderId(dir.getParent(), rootDir);

        return !currentName.equals(entity.getName()) ||
               !currentPath.equals(entity.getFolderPath()) ||
               !Objects.equals(currentParentId, entity.getParentFolderId());
    }

    private Long getParentFolderId(Path parentDir, Path rootDir) {
        if (parentDir == null || !parentDir.startsWith(rootDir)) return null;
        Path parentInfo = parentDir.resolve(".folder_info.json");
        return Files.exists(parentInfo) ? readFolderIdFromJson(parentInfo) : null;
    }

    private FolderTreeEntity createNewEntity(Path dir, Path rootDir, long folderId) {
        return FolderTreeEntity.builder()
                .folderId(folderId)
                .name(dir.getFileName().toString())
                .folderPath(dir.toString())
                .parentFolderId(getParentFolderId(dir.getParent(), rootDir))
                .subscriptionCode(type.toUpperCase())  // type 변수는 해당 메서드 또는 클래스 내에 선언되어 있어야 함
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .hasFiles(hasFiles(dir))
                .build();
    }

    private boolean hasFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && !path.getFileName().toString().equals(".folder_info.json")) {
                    return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void updateDatabase(Map<Long, FolderTreeEntity> folderMap) {
        List<Long> toDelete = repository.findAll()
                .map(FolderTreeEntity::getFolderId)
                .filter(id -> !folderMap.containsKey(id))
                .collectList()  // Mono<List<Long>>로 변환
                .block();
        List<FolderTreeEntity> toSave = folderMap.values().stream()
                .filter(e -> !"U".equals(e.getChangeCd()))
                .collect(Collectors.toList());

        repository.deleteAllById(toDelete).block();

        repository.saveAll(toSave)
                .collectList() // Flux를 Mono<List<FolderTreeEntity>>로 변환
                .block();      // 동기적으로 List<FolderTreeEntity>를 반환
    }

    // JSON 처리 유틸리티
    private Long readFolderIdFromJson(Path file) {
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            return root.get("folder_id").asLong();
        } catch (IOException e) {
            throw new RuntimeException("JSON 읽기 실패", e);
        }
    }

    private void writeFolderInfoJson(Path file, Long folderId) {
        try {
            ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.put("folder_id", folderId);
            new ObjectMapper().writeValue(file.toFile(), json);
        } catch (IOException e) {
            throw new RuntimeException("JSON 쓰기 실패", e);
        }
    }
}
