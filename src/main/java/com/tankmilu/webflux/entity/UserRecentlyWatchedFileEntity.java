package com.tankmilu.webflux.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_recently_watched_file")
public class UserRecentlyWatchedFileEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("user_id")
    private String userId;

    @Column("file_id")
    private Long fileId;

    @Column("position_sec")
    private Integer positionSec;

    @Column("duration_sec")
    private Integer durationSec;

    @Column("progress")
    private Integer progress;

    @Column("watched_at")
    private LocalDateTime watchedAt;
}
