package com.srs.live.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StartLiveResponse {
    private String whipUrl;
    private String streamId;
    private String srsNode;
}