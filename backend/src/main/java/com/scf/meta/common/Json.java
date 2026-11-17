package com.scf.meta.common;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** JSON 工具：统一使用 Spring Boot 自动配置的 Jackson ObjectMapper。 */
@Component
public class Json {
    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode read(String content) {
        try {
            return mapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("payload 不是合法 JSON: " + e.getMessage());
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
