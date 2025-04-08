package com.tankmilu.batch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tankmilu.webflux.entity.folder.AnimationFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.DramaFolderTreeEntity;
import com.tankmilu.webflux.entity.folder.FolderTreeEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service
public class FolderSyncServce {

    private Map<Long, FolderTreeEntity> folderMap = new HashMap<>();

    // 최대 folder_id 값을 저장
    private long maxFolderId = 0;

    // base directory (예: application.yml 또는 하드코딩으로 지정)
    private String baseDirPath = "/path/to/your/base/directory";

    // ObjectMapper: JSON 처리용 (Jackson)
    private ObjectMapper objectMapper = new ObjectMapper();

}
