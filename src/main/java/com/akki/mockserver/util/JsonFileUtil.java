package com.akki.mockserver.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonFileUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static <T> List<T> readJsonFile(String filePath, Class<T> clazz) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
            objectMapper.writeValue(file, List.of());
        }
        return objectMapper.readValue(file, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    public static <T> void writeJsonFile(String filePath, List<T> data) throws IOException {
        objectMapper.writeValue(new File(filePath), data);
    }
}
