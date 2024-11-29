package com.akki.mockserver.service;

import com.akki.mockserver.model.Environment;
import com.akki.mockserver.model.MockService;
import com.akki.mockserver.util.JsonFileUtil;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service // This annotation registers the class as a Spring Bean (Service Layer)
public class MockServerService {

    private static final String ENV_FILE_PATH = "environments.json";
    private static final String SERVICE_FILE_PATH = "services.json";

    // Get all environments
    public List<Environment> getEnvironments() throws IOException {
        return JsonFileUtil.readJsonFile(ENV_FILE_PATH, Environment.class);
    }

    public void saveEnvironments(List<Environment> environments) throws IOException {
        JsonFileUtil.writeJsonFile(ENV_FILE_PATH, environments);
    }

    // Get all services
    public List<MockService> getServices() throws IOException {
        return JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
    }

    public void saveServices(List<MockService> services) throws IOException {
        JsonFileUtil.writeJsonFile(SERVICE_FILE_PATH, services);
    }

    // Get services by environment
    public List<MockService> getServicesForEnv(String env) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        return services.stream()
            .filter(service -> service.getEnv().equals(env))
            .collect(Collectors.toList());
    }

    // Get a matching service based on the path and HTTP method
    public Optional<MockService> getMatchingService(String env, String remainingPath, String httpMethod) throws IOException {
        List<MockService> services = getServices();
        return services.stream()
            .filter(service -> service.getEnv().equals(env) &&
                matchesPattern(service.getUrl(), remainingPath) &&
                service.getHttpMethod().equals(httpMethod))
            .findFirst();
    }

    private boolean matchesPattern(String pattern, String url) {
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.matches();
    }

    public String evaluateResponse(String script, String requestBody) {
        Value responseJson;
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("requestBody", requestBody);
            context.eval("js", script);
            responseJson = context.getBindings("js").getMember("responseJson");
            return responseJson.asString();
        } catch (Exception e) {
            return e.getMessage() + ". Please check your JavaScript code for errors";
        }
    }
}
