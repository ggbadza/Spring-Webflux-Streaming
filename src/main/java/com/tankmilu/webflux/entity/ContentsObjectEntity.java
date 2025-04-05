package com.tankmilu.webflux.entity;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table()
public class ContentsObjectEntity implements Persistable<Long> {

    @Id
    @Column("contents_id")
    @Getter
    private Long contentsId;

    @Column("title")
    @Getter
    private String title;

    @Column("description")
    @Getter
    private String description;

    @Column("thumbnail_path")
    @Getter
    private String thumbnailPath;

    @Column("type")
    @Getter
    private String type;

    @Column("folder_id")
    @Getter
    private Long folderId;

    @Transient
    private boolean isNewRecord;

    @Override
    public Long getId() {
        return this.contentsId;
    }

    @Override
    public boolean isNew() {
        return this.isNewRecord;
    }

}
