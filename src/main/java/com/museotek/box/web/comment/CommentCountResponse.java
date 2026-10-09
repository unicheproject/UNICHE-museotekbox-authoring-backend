package com.museotek.box.web.comment;

import com.museotek.box.application.comment.CountOpenCommentsQuery;

import java.util.UUID;

public record CommentCountResponse(UUID projectId, long open) {

    public static CommentCountResponse from(CountOpenCommentsQuery.OpenCount count) {
        return new CommentCountResponse(count.projectId(), count.open());
    }
}
