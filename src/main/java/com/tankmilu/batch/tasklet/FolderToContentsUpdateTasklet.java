package com.tankmilu.batch.tasklet;

import com.tankmilu.webflux.entity.ContentsObjectEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.repository.ContentsObjectRepository;
import com.tankmilu.webflux.repository.folder.FolderTreeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class FolderToContentsUpdateTasklet<T extends FolderTreeEntity> implements Tasklet {

    private final FolderTreeRepository<T> folderRepository;
    private final ContentsObjectRepository contentsRepository;
    private final String contentType; // "anime", "movie", "drama" 등

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                ChunkContext chunkContext) {
        log.info("FolderToContentsUpdateTasklet 시작 - 타입: {}", contentType);

        // 1. FolderTreeEntity에서 데이터 로드 (폴더 ID로 그룹화)
        List<T> folderEntities = folderRepository.findAll()
                .collectList()
                .block();

        if (folderEntities == null || folderEntities.isEmpty()) {
            log.info("폴더 데이터가 없습니다. 타입: {}", contentType);
            return RepeatStatus.FINISHED;
        }

        Map<Long, T> allFoldersMap  = new HashMap<>();
        for (T folder : folderEntities) {
            allFoldersMap .put(folder.getFolderId(), folder);
        }

        // hasFiles가 true이면서 부모 폴더중에 true가 없는 폴더만 처리
        Map<Long, T> folderEntityMap = new HashMap<>();

        for (T folder : folderEntities) {
            if (folder.getHasFiles() != null && folder.getHasFiles()) {
                // 부모 폴더 체인에 hasFiles=true인 폴더가 있는지 확인
                boolean includeFolder = true;
                Long parentId = folder.getParentFolderId();

                // 부모 폴더를 타고 올라가며 체크
                while (parentId != null) {
                    T parentFolder = allFoldersMap.get(parentId);
                    if (parentFolder == null) {
                        break; // 부모 폴더를 찾을 수 없는 경우
                    }

                    // 부모 폴더가 hasFiles=true라면 현재 폴더는 제외
                    if (parentFolder.getHasFiles() != null && parentFolder.getHasFiles()) {
                        includeFolder = false;
                        break;
                    }

                    // 다음 부모로 이동
                    parentId = parentFolder.getParentFolderId();
                }

                // 모든 조건을 만족하면 맵에 추가
                if (includeFolder) {
                    folderEntityMap.put(folder.getFolderId(), folder);
                }
            }
        }


        log.info("로드된 폴더 엔티티 수: {}", folderEntities.size());

        // 2. ContentsObjectEntity 로드 (폴더 ID로 그룹화)
        List<ContentsObjectEntity> existingContents = contentsRepository.findAll()
                .collectList()
                .block();

        Map<Long, ContentsObjectEntity> existingContentsMap = new HashMap<>();
        if (existingContents != null) {
            for (ContentsObjectEntity content : existingContents) {
                if (content.getFolderId() != null) {
                    existingContentsMap.put(content.getFolderId(), content);
                }
            }
        }

        log.info("기존 콘텐츠 엔티티 수: {}", existingContentsMap.size());

        // 3. 파일이 있는 폴더에 대해 ContentsObjectEntity 업데이트 또는 생성
        List<ContentsObjectEntity> contentsToSave = new ArrayList<>();

        // folderEntityMap을 순회하면서 existingContentsMap에 없는 경우만 처리
        for (Map.Entry<Long, T> entry : folderEntityMap.entrySet()) {
            Long folderId = entry.getKey();
            T folder = entry.getValue();

            // existingContentsMap에 해당 폴더ID가 없는 경우에만 새 엔티티 생성
            if (!existingContentsMap.containsKey(folderId)) {
                ContentsObjectEntity newContent = createNewContentsEntity(folder);
                log.debug("새 콘텐츠 엔티티 생성: {}", folder.getName());
                contentsToSave.add(newContent);
            } else {
                log.debug("이미 콘텐츠가 존재하는 폴더: {}", folder.getName());
            }
        }

        log.info("저장할 새 콘텐츠 엔티티 수: {}", contentsToSave.size());

        // 4. 저장
        if (!contentsToSave.isEmpty()) {
            contentsRepository.saveAll(contentsToSave)
                    .collectList()
                    .block();
            log.info("콘텐츠 엔티티 저장 완료");
        }

        return RepeatStatus.FINISHED;
    }

    private ContentsObjectEntity createNewContentsEntity(T folder) {
        // 현재 날짜에서 연도와 월 추출
        LocalDateTime now = LocalDateTime.now();
        String yearMonth = String.format("%d%02d", now.getYear(), now.getMonthValue());

        return ContentsObjectEntity.builder()
                .title(folder.getName())
                .releaseYM(yearMonth)
                .type(contentType)
                .folderId(folder.getFolderId())
                .modifiedAt(folder.getModifiedAt())
                .build();
    }
}
