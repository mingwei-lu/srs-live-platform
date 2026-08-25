package com.srs.live.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SrsCallbackRequest {
    @JsonProperty("action")
    private String action;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("ip")
    private String ip;

    @JsonProperty("vhost")
    private String vhost;

    @JsonProperty("app")
    private String app;

    @JsonProperty("stream")
    private String stream;

    @JsonProperty("param")
    private String param;

    @JsonProperty("srs_node_id")
    private String srsNodeId;

    public String getQuery() {
        return param;
    }
}