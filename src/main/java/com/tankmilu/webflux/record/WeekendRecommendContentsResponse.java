package com.tankmilu.webflux.record;

import java.util.List;

public record WeekendRecommendContentsResponse(
        String description,
        List<WeekendContentsResponse> mondayContentsResponseList,
        List<WeekendContentsResponse> tuesdayContentsResponseList,
        List<WeekendContentsResponse> wednesdayContentsResponseList,
        List<WeekendContentsResponse> thursdayContentsResponseList,
        List<WeekendContentsResponse> fridayContentsResponseList,
        List<WeekendContentsResponse> saturdayContentsResponseList,
        List<WeekendContentsResponse> sundayContentsResponseList,
        List<WeekendContentsResponse> specialBroadcastContentsResponseList
) {
}
