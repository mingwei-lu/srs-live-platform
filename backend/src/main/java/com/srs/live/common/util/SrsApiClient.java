package com.srs.live.common.util;

import com.srs.live.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

@Slf4j
@Component
public class SrsApiClient {

    private final RestTemplate restTemplate;
    private final String apiPassword;

    public SrsApiClient(@Value("${srs.srs.api-password}") String apiPassword) {
        this.apiPassword = apiPassword;
        this.restTemplate = new RestTemplate();
    }

    public void deleteClient(String srsHost, int apiPort, String clientId) {
        try {
            String url = "http://" + srsHost + ":" + apiPort + "/api/v1/clients/" + clientId;
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            log.info("SRS delete client success: host={}, clientId={}", srsHost, clientId);
        } catch (Exception e) {
            log.error("SRS delete client failed: host={}, clientId={}", srsHost, clientId, e);
            throw new BusinessException(50001, "SRS API call failed: " + e.getMessage());
        }
    }

    public String listClients(String srsHost, int apiPort) {
        try {
            String url = "http://" + srsHost + ":" + apiPort + "/api/v1/clients";
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("SRS list clients failed: host={}", srsHost, e);
            return null;
        }
    }

    public boolean healthCheck(String srsHost, int apiPort) {
        try {
            String url = "http://" + srsHost + ":" + apiPort + "/api/v1/versions";
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = "admin:" + apiPassword;
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}