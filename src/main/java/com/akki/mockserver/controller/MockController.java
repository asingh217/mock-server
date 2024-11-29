package com.akki.mockserver.controller;

import com.akki.mockserver.model.Environment;
import com.akki.mockserver.model.MockService;
import com.akki.mockserver.util.JsonFileUtil;
import com.google.gson.Gson;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

@Controller
public class MockController {

    private static final String ENV_FILE_PATH = "environments.json";
    private static final String SERVICE_FILE_PATH = "services.json";

    /*
     * Environments
     */
    
    // Display the Environments
    @GetMapping("/envs")
    public String getEnvironmentsPage(Model model) throws IOException {
        List<Environment> environments = JsonFileUtil.readJsonFile(ENV_FILE_PATH, Environment.class);
        model.addAttribute("environments", new Gson().toJson(environments));
        return "envList"; // Ensure this matches the name of your Thymeleaf template
    }

    @PostMapping("/envs")
    public String addEnvironment(@RequestBody Environment request, Model model) throws IOException {
        List<Environment> environments = JsonFileUtil.readJsonFile(ENV_FILE_PATH, Environment.class);
        request.setId(UUID.randomUUID().toString());
        environments.add(request);
        JsonFileUtil.writeJsonFile(ENV_FILE_PATH, environments);
        model.addAttribute("environments", new Gson().toJson(environments));
        return "envList";
    }
    

    @DeleteMapping("/envs/{id}")
    public String deleteEnvironment(@PathVariable String id, Model model) throws IOException {
        List<Environment> environments = JsonFileUtil.readJsonFile(ENV_FILE_PATH, Environment.class);
        environments = environments.stream()
                .filter(env -> !env.getId().equals(id))
                .collect(Collectors.toList());
        JsonFileUtil.writeJsonFile(ENV_FILE_PATH, environments);
        model.addAttribute("environments", new Gson().toJson(environments));
        return "envList";
    }

    // @GetMapping("/envs/search")
    // @ResponseBody
    // public List<Environment> searchEnvironments(@RequestParam String query) throws IOException {
    //     List<Environment> environments = JsonFileUtil.readJsonFile(ENV_FILE_PATH, Environment.class);
    //     return environments.stream()
    //             .filter(env -> env.getEnv().toLowerCase().startsWith(query.toLowerCase()))
    //             .collect(Collectors.toList());
    // }

    /*
     * Services List
     */

    @GetMapping("/envs/{env}/services")
    public String getServiceListPage(@PathVariable String env, Model model) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        services = services.stream()
                .filter(service -> service.getEnv().equals(env))
                .collect(Collectors.toList());
        model.addAttribute("env", env);
        model.addAttribute("services", new Gson().toJson(services));
        return "serviceList";
    }

    @PostMapping("/envs/{env}/services")
    public String addService(@PathVariable String env, @RequestBody MockService request, Model model) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        request.setId(UUID.randomUUID().toString());
        request.setEnv(env);
        services.add(request);
        JsonFileUtil.writeJsonFile(SERVICE_FILE_PATH, services);
        model.addAttribute("env", env);
        model.addAttribute("services", new Gson().toJson(services));
        return "serviceList";
    }

    @GetMapping("/envs/{env}/services/create")
    public String getMockService(@PathVariable String env, Model model) {
        model.addAttribute("env", env);
        // Your existing code for handling mock services
        return "createService";
    }

    @PutMapping("/envs/{env}/services/{id}")
    public String editService(@PathVariable String env, @PathVariable String id, @RequestBody MockService request, Model model) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        int servicesTotalCount = services.size();
        services = services.stream()
            .filter(service -> !service.getId().equals(id))
            .collect(Collectors.toList());
            int filteredServicesTotalCount = services.size();
        if(servicesTotalCount > filteredServicesTotalCount){
            request.setId(id);
            request.setEnv(env);
            services.add(request);
            JsonFileUtil.writeJsonFile(SERVICE_FILE_PATH, services);
        }
        model.addAttribute("env", env);
        model.addAttribute("services", new Gson().toJson(services));
        return "serviceList";
    }

    @GetMapping("/envs/{env}/services/{id}")
    public String getService(@PathVariable String env, @PathVariable String id, Model model) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        MockService mockService;
        try{
            mockService = services.stream()
                .filter(service -> service.getId().equals(id))
                .findFirst().get();
        } catch (Exception err){
            model.addAttribute("env", env);
            return "serviceList";
        }
        String mockString = new Gson().toJson(mockService);
        model.addAttribute("env", env);
        model.addAttribute("id", id);
        model.addAttribute("mockService", mockString);
        return "servicePage";
    }

    @DeleteMapping("/envs/{env}/services/{id}")
    public String deleteService(@PathVariable String env, @PathVariable String id, Model model) throws IOException {
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        services = services.stream()
                .filter(service -> !service.getId().equals(id))
                .collect(Collectors.toList());
        JsonFileUtil.writeJsonFile(SERVICE_FILE_PATH, services);
        model.addAttribute("env", env);
        model.addAttribute("services", new Gson().toJson(services));
        return "serviceList";
    }

    @RequestMapping("envs/{env}/**")
    public ResponseEntity<String> handleRequestBasedOnEnv(@PathVariable String env, HttpServletRequest request) throws IOException
    { String requestURI = request.getRequestURI();
        String remainingPath = requestURI.substring(requestURI.indexOf(env) + env.length() + 1);
        List<MockService> services = JsonFileUtil.readJsonFile(SERVICE_FILE_PATH, MockService.class);
        Optional<MockService> matchingService = services.stream()
            .filter(service -> service.getEnv().equals(env) 
            && matchesPattern(service.getUrl(), remainingPath)
            && service.getHttpMethod().equals(request.getMethod()))
            .findFirst();
        if (matchingService.isPresent())
        {
            MockService service = matchingService.get();
            HttpHeaders headers = new HttpHeaders();
            service.getResponseHeaders().forEach( (key, value) -> {
                headers.add(key, value);
            });
            String responseBody = service.getResponseBody();
            if(service.getResponseBodyType().equals("dynamic"))
                responseBody = evaluateResponse(responseBody);
            return new ResponseEntity<>(responseBody, headers, Integer.parseInt(service.getStatusCode()));
        } else {
            return new ResponseEntity<>("Service not found", HttpStatus.NOT_IMPLEMENTED);
        }
    }

    private String evaluateResponse(String script){
        Value responseJson;
        try (Context context = Context.create()) {
            context.eval("js", script);
            responseJson = context.getBindings("js").getMember("responseJson");
            System.out.println(responseJson.asString());
            return responseJson.asString();
        } catch (Exception e) {
            return e.getMessage() + ". Please check your javascript code for errors";
        }
    }

    private boolean matchesPattern(String pattern, String url)
    {
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        return matcher.matches();
    }

}