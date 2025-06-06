package com.tankmilu.webflux.es.document;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Document(indexName = "contents")
@Getter
@Builder
public class ContentsObjectDocument {

    @Id
    private Long contentsId;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean")
    private String description;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Long, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Date)
    private LocalDateTime modifiedAt;

    @Field(type = FieldType.Text, analyzer = "korean")
    private List<String> keywords;
}