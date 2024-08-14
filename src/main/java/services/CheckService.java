package services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.script.ScriptException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import models.CodeCheck;

@ApplicationScoped
public class CheckService {
    @Inject private CodeCheck codeCheck;
    private ObjectMapper mapper = new ObjectMapper();

    public String checkHTML(String repo, String problem, String ccid, Map<Path, String> submissionFiles)
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        long startTime = System.nanoTime();
        String report = codeCheck.run("html", repo, problem, ccid, submissionFiles);
        if (report == null || report.length() == 0) {
            double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
            report = String.format("Timed out after %5.0f seconds\n", elapsed);
        }
        return report;
    }
    
    public String run(Map<Path, String> submissionFiles)
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        long startTime = System.nanoTime();
        String report = codeCheck.run("Text", submissionFiles);
        double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
        if (report == null || report.length() == 0) {
            report = String.format("Timed out after %5.0f seconds\n", elapsed);
        }
        return report;
    }

    public String runFileUpload(String repo, Map<Path, String> submissionFiles) 
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        // Your logic here, for example:
        String report = codeCheck.run("FileUpload",submissionFiles);
        if (report == null || report.isEmpty()) {
            report = "No report generated.";
        }
        return report;
    }

    public String runFormPost(MultivaluedMap<String, String> formParams) {
    StringBuilder report = new StringBuilder("Processed form data:\n");

    for (Map.Entry<String, List<String>> entry : formParams.entrySet()) {
        report.append(entry.getKey()).append(": ");
        entry.getValue().forEach(value -> report.append(value).append(", "));
        report.setLength(report.length() - 2); // Remove the last comma and space
        report.append("\n");
    }

    return report.toString();
}

    public ObjectNode runJSON(JsonNode json)
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        Map<Path, String> submissionFiles = new TreeMap<>();
        Iterator<Entry<String, JsonNode>> iter = json.fields();
        while (iter.hasNext()) {
            Entry<String, JsonNode> entry = iter.next();
            submissionFiles.put(Paths.get(entry.getKey()), entry.getValue().asText());         
        }
        String report = codeCheck.run("JSON", submissionFiles);
        return (ObjectNode) mapper.readTree(report);
    }

    public ObjectNode checkNJS(String repo, String problem, String ccid, Map<Path, String> submissionFiles)
            throws NoSuchMethodException, IOException, InterruptedException, ScriptException {
        ObjectNode studentWork = JsonNodeFactory.instance.objectNode();
        Map<Path, byte[]> reportZipFiles = new TreeMap<>();
        for (var e : submissionFiles.entrySet()) {
            Path p = e.getKey();
            submissionFiles.put(p, e.getValue());
            reportZipFiles.put(p, e.getValue().getBytes(StandardCharsets.UTF_8));
            studentWork.put(p.toString(), e.getValue());
        }

        String report = codeCheck.run("NJS", repo, problem, ccid, submissionFiles);
        ObjectNode result = (ObjectNode) mapper.readTree(report);
        String reportHTML = result.get("report").asText();
        reportZipFiles.put(Paths.get("report.html"), reportHTML.getBytes(StandardCharsets.UTF_8));

        byte[] reportZipBytes = codeCheck.signZip(reportZipFiles);
        String reportZip = Base64.getEncoder().encodeToString(reportZipBytes);
        result.put("zip", reportZip);
        return result;
    }
}
