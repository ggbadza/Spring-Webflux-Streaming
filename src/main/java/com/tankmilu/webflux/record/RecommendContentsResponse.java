package com.tankmilu.webflux.record;

import java.util.List;

public record RecommendContentsResponse(
        String userId,
        Integer recommendSeq,
        String description,
        List<ContentsResponse> contentsResponseList
) {
}
