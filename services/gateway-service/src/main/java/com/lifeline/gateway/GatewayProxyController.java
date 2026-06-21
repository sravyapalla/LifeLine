package com.lifeline.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

@RestController
public class GatewayProxyController {
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final RestClient operationsRestClient;

    public GatewayProxyController(RestClient operationsRestClient) {
        this.operationsRestClient = operationsRestClient;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/platform")) {
            return ResponseEntity.notFound().build();
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return operationsRestClient
                .method(method)
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(requestUri);
                    if (request.getQueryString() != null) {
                        builder.query(request.getQueryString());
                    }
                    return builder.build();
                })
                .headers(headers -> copyRequestHeaders(request, headers))
                .body(body == null ? new byte[0] : body)
                .exchange((clientRequest, clientResponse) -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    clientResponse.getHeaders().forEach((name, values) -> {
                        if (!isHopByHop(name)) {
                            responseHeaders.addAll(name, values);
                        }
                    });
                    byte[] responseBody = StreamUtils.copyToByteArray(clientResponse.getBody());
                    return ResponseEntity.status(clientResponse.getStatusCode())
                            .headers(responseHeaders)
                            .body(responseBody);
                });
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (isHopByHop(name)) {
                return;
            }
            Collections.list(request.getHeaders(name)).forEach(value -> headers.add(name, value));
        });
    }

    private static boolean isHopByHop(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }
}
