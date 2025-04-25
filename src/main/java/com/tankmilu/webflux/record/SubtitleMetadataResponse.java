package com.tankmilu.webflux.record;

import java.util.List;

public record SubtitleMetadataResponse(
        String hasSubtitle,
        int count,
        List<SubtitleInfo> subtitleList
) {
}
