package com.tankmilu.webflux.entity.folder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table()
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DramaFolderTreeEntity extends FolderTreeEntity {
}
