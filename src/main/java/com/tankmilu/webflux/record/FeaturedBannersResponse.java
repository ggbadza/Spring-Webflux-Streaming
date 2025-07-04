package com.tankmilu.webflux.record;

import java.math.BigDecimal;

public record FeaturedBannersResponse(
        Long sequenceId,
        Long contentsId,
        String title,
        String description,
        String type,
        BigDecimal userRating,
        String posterUrl,
        String thumbnailUrl,
        String seriesId,
        String season) {

    public static FeaturedBannersResponse fromEntity(com.tankmilu.webflux.entity.FeaturedBannersEntity entity) {
        return new FeaturedBannersResponse(
                entity.getId(),
                entity.getContentsId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getType(),
                entity.getUserRating(),
                entity.getPosterUrl(),
                entity.getThumbnailUrl(),
                entity.getSeriesId(),
                entity.getSeason()
        );
    }
}
