package com.tankmilu.webflux.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

@Table()
public class ContentsFileEntity implements Persistable<Long>, Serializable {

    @Id
    @Getter
    @Column("file_id")
    private Long fileId;

    @Getter
    @Column("file_name")
    private String fileName;        // 파일 이름

    @Getter
    @Setter
    @Column("file_path")
    private String filePath;        // 실제 파일 경로

    @Getter
    @Setter
    @Column("contents_id")
    private Long contentsId;        // 컨텐츠 ID (인덱스)

    @Getter
    @Setter
    @Column("subtitle_path")
    private String subtitlePath;    // 자막 파일 경로

    @Getter
    @Column("resolution")
    private String resolution;      // 파일 해상도 (예: "1080")

    @CreatedDate
    @Getter
    @Column("created_at")
    private LocalDateTime createdAt; // 파일 생성 날짜

    @Getter
    @Setter
    @Column("subtitle_created_at")
    private LocalDateTime subtitleCreatedAt; // 파일 생성 날짜

    @Setter
    @Transient
    private boolean isNewRecord;

    @Builder
    public ContentsFileEntity(Long fileId,
                              String fileName,
                              String filePath,
                              String subtitlePath,
                              String resolution,
                              Long contentsId,
                              LocalDateTime subtitleCreatedAt) {
        this.fileId       = fileId;
        this.fileName     = fileName;
        this.filePath     = filePath;
        this.subtitlePath = subtitlePath;
        this.resolution   = resolution;
        this.contentsId   = contentsId;
        this.subtitleCreatedAt = subtitleCreatedAt;
        this.isNewRecord  = true;
    }

    @Override
    public Long getId() {
        return this.fileId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }

    public LocalDateTime setSubtitleCreatedAtNow() {
        return this.subtitleCreatedAt = LocalDateTime.now();
    }
}
