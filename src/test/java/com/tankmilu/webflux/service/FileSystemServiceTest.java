package com.tankmilu.webflux.service;

import com.tankmilu.webflux.entity.folder.AnimationFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import com.tankmilu.webflux.record.DirectoryRecord;
import com.tankmilu.webflux.repository.folder.AnimationFolderTreeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class FileSystemServiceTest {

    @Autowired
    private FileSystemService fileSystemService;

    @Autowired
    private AnimationFolderTreeRepository folderTreeRepository;

    @Test
    void fileSystemServiceTest_ExistsFiles() {
        List<String> list = fileSystemService.getFileList("ani", 2L,"100").block();  // block()으로 결과 대기
        System.out.println(list);
    }

    @Test
    void getFolderListTest_WithTemporaryFolders() {
        // 임시 부모 폴더 (folderId: 9999) 생성 (parentFolderId는 null)
        AnimationFolderTreeEntity parentFolder = (AnimationFolderTreeEntity) createTestFolder(
                9999L, "tempParent", "/tmp/parent", null, "1", false);

        // 임시 자식 폴더 (folderId: 10000 ~ 10005) 생성, parentFolderId는 9999로 설정
        List<AnimationFolderTreeEntity> childFolders = new ArrayList<>();
        for (long id = 10000; id <= 10005; id++) {
            childFolders.add(createTestFolder(
                    id, "tempChild" + id, "/tmp/child" + id, 9999L, "1", false));
        }

        // DB에 임시 폴더 저장 (reactive 방식으로 block() 처리)
        folderTreeRepository.save(parentFolder).block();
        folderTreeRepository.saveAll(childFolders).collectList().block();

        // 테스트 대상: parentFolderId가 9999인 폴더 조회
        List<DirectoryRecord> folderList = fileSystemService.getFolderList("ani",9999L,"100").block();
        System.out.println(folderList);

        // 검증: 결과에 자식 폴더들 (10000~10005)가 모두 포함되어야 함
        Set<Long> expectedChildIds = LongStream.rangeClosed(10000, 10005)
                .boxed()
                .collect(Collectors.toSet());

        Set<Long> actualChildIds = folderList.stream()
                .map(DirectoryRecord::folderId)
                .collect(Collectors.toSet());

        assertEquals(expectedChildIds, actualChildIds);

        // 테스트 후 DB에서 임시 폴더 삭제 (트랜잭션 어노테이션 없이 직접 삭제)
        folderTreeRepository.deleteAll(childFolders).block();
        folderTreeRepository.delete(parentFolder).block();
    }

    @Test
    void getVideoFileInfoTest_ExistsSubs() {
//        FolderTreeEntity parentFolder = createTestFolder(9999L, "tempParent", "C:\\Temp", null, "1", true);
//        folderTreeRepository.save(parentFolder).block();
          fileSystemService.getVideoFileInfo("ani",2L, "test.mkv","100")
                .doOnNext(System.out::println)  // 출력 확인
                .block();
//        folderTreeRepository.delete(parentFolder).block();
    }

    private AnimationFolderTreeEntity createTestFolder(Long folderId, String name, String folderPath,
                                                       Long parentFolderId, String permission, boolean hasFiles) {
        AnimationFolderTreeEntity folder = new AnimationFolderTreeEntity();
        ReflectionTestUtils.setField(folder, "folderId", folderId);
        ReflectionTestUtils.setField(folder, "name", name);
        ReflectionTestUtils.setField(folder, "folderPath", folderPath);
        ReflectionTestUtils.setField(folder, "parentFolderId", parentFolderId);
        ReflectionTestUtils.setField(folder, "permission", permission);
        ReflectionTestUtils.setField(folder, "hasFiles", hasFiles);
        ReflectionTestUtils.setField(folder, "isNewRecord",true);
        return folder;
    }

}
