package com.tankmilu.webflux.es.repository;

import com.tankmilu.webflux.es.document.ContentsObjectDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import reactor.core.publisher.Flux;

public interface ContentsObjectDocumentRepository extends ReactiveElasticsearchRepository<ContentsObjectDocument, Long> {

    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"title\", \"description\", \"keywords\"], \"type\": \"best_fields\", \"analyzer\": \"korean\"}}")
    Flux<ContentsObjectDocument> searchByQueryInTitleAndDescriptionAndKeywords(String query);

    @Query("{\"match\": {\"title\": {\"query\": \"?0\", \"analyzer\": \"korean\"}}}")
    Flux<ContentsObjectDocument> searchByQueryInTitle(String query);

    Flux<ContentsObjectDocument> searchByTitle(String query);

    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"description\", \"keywords\"], \"type\": \"best_fields\", \"analyzer\": \"korean\"}}")
    Flux<ContentsObjectDocument> searchByQueryInDescriptionAndKeywords(String query);

}
