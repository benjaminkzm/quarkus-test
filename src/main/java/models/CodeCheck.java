package models;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.horstmann.codecheck.Main;
import com.horstmann.codecheck.Plan;
import com.horstmann.codecheck.Problem;
import com.horstmann.codecheck.ResourceLoader;
import com.horstmann.codecheck.Util;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Singleton
public class CodeCheck {
    private static final Logger logger = Logger.getLogger(CodeCheck.class);    
    private ProblemConnector probConn;
    private JarSigner signer;
    private ResourceLoader resourceLoader;

    @Inject
    public CodeCheck(@ConfigProperty(name = "com.horstmann.codecheck.storeLocation") String keyStorePath,
                     @ConfigProperty(name = "com.horstmann.codecheck.storePassword") String storePassword,
                     ProblemConnector probConn) {
        this.probConn = probConn;
        this.resourceLoader = new ResourceLoader() {
            @Override
            public InputStream loadResource(String path) throws IOException {
                return getClass().getClassLoader().getResourceAsStream("public/resources/" + path);
            }
            @Override
            public String getProperty(String key) {
                return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(null);
            }
        };
        try {
            char[] password = storePassword.toCharArray();
            KeyStore ks = KeyStore.getInstance(new File(keyStorePath), password);
            KeyStore.ProtectionParameter protParam = new KeyStore.PasswordProtection(password); 
            KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) ks.getEntry("codecheck", protParam);
            signer = new JarSigner.Builder(pkEntry).build();            
        } catch (Exception e) {
            logger.warn("Cannot load keystore", e);
        }
    }

    public Map<Path, byte[]> loadProblem(String repo, String problemName, String studentId) throws IOException, ScriptException, NoSuchMethodException {
        Map<Path, byte[]> problemFiles = loadProblem(repo, problemName);
        replaceParametersInDirectory(studentId, problemFiles);
        return problemFiles;
    }

    public boolean replaceParametersInDirectory(String studentId, Map<Path, byte[]> problemFiles)
            throws ScriptException, NoSuchMethodException, IOException {
        Path paramPath = Path.of("param.js"); 
        if (problemFiles.containsKey(paramPath)) {
            ScriptEngineManager engineManager = new ScriptEngineManager();
            ScriptEngine engine = engineManager.getEngineByName("nashorn");
            InputStream in = resourceLoader.loadResource("preload.js");
            engine.eval(new InputStreamReader(in, StandardCharsets.UTF_8));
            ((Invocable) engine).invokeMethod(engine.get("Math"), "seedrandom", studentId);
            engine.eval(Util.getString(problemFiles, paramPath));
            for (Path p : Util.filterNot(problemFiles.keySet(), "param.js", "*.jar", "*.gif", "*.png", "*.jpg", "*.wav")) {
                String contents = new String(problemFiles.get(p), StandardCharsets.UTF_8);
                String result = replaceParametersInFile(contents, engine);
                if (result != null)
                    problemFiles.put(p, result.getBytes(StandardCharsets.UTF_8));               
            }
            return true;
        } else {
            return false;
        }
    }
    
    private String replaceParametersInFile(String contents, ScriptEngine engine) throws ScriptException, IOException {
        if (contents == null) return null; // Happens if not UTF-8
        String leftDelimiter = (String) engine.eval("delimiters[0]");
        int leftLength = leftDelimiter.length();
        String rightDelimiter = (String) engine.eval("delimiters[1]");
        int rightLength = rightDelimiter.length();
        StringBuilder result = new StringBuilder();
        int from = 0;
        int to = -rightLength;
        boolean done = false;
        while (!done) {
            from = contents.indexOf(leftDelimiter, to + rightLength);
            if (from == -1) {
                if (to == -1) return null; // No delimiter in file
                else {
                    result.append(contents.substring(to + rightLength));
                    done = true;
                }
            } else {
                int nextTo = contents.indexOf(rightDelimiter, from + leftLength);
                if (nextTo == -1) return null; // Delimiters don't match--might be binary file
                else {
                    result.append(contents.substring(to + rightLength, from));
                    to = nextTo;
                    String toEval = contents.substring(from + leftLength, to);
                    if (toEval contains(leftDelimiter)) return null; // Nested
                    result.append("" + engine.eval(toEval));
                }
            }
        }
        return result.toString();
    }
    
    public Map<Path, byte[]> loadProblem(String repo, String problemName) throws IOException {
        Map<Path, byte[]> result;
        byte[] zipFile = probConn.read(repo, problemName);
        result = Util.unzip(zipFile);
        return result;
    }

    public void saveProblem(String repo, String problem, Map<Path, byte[]> problemFiles) throws IOException {   
        byte[] problemZip = Util.zip(problemFiles);
        probConn.write(problemZip, repo, problem);  
    }

    public String run(String reportType, String repo, String problem, String ccid, Map<Path, String> submissionFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        Map<Path, byte[]> problemFiles = loadProblem(repo, problem, ccid);
        boolean save = !problemFiles.containsKey(Path.of("param.js")) && 
            !problemFiles.keySet().stream().anyMatch(p -> p.startsWith("_outputs"));
        Properties metaData = new Properties();
        metaData.put("User", ccid);
        metaData.put("Problem", (repo + "/" + problem).replaceAll("[^\\pL\\pN_/-]", ""));
        
        Plan plan = new Main().run(submissionFiles, problemFiles, reportType, metaData, resourceLoader);
        if (save) {
            plan.writeSolutionOutputs(problemFiles);
            saveProblem(repo, problem, problemFiles);
        }

        return plan.getReport().getText();
    }
    
    public String run(String reportType, Map<Path, String> submissionFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        Map<Path, byte[]> problemFiles = new TreeMap<>();
        for (var entry : submissionFiles.entrySet()) {
            var key = entry.getKey();
            problemFiles.put(key, entry.getValue().getBytes());
        }
        Properties metaData = new Properties();
        Plan plan = new Main().run(submissionFiles, problemFiles, reportType, metaData, resourceLoader);
        return plan.getReport().getText();
    }    
    
    public String checkAndSave(String problem, Map<Path, byte[]> originalProblemFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        Map<Path, byte[]> problemFiles = new TreeMap<>(originalProblemFiles);
        String studentId = com.horstmann.codecheck.Util.createPronouncableUID();
        boolean isParametric = replaceParametersInDirectory(studentId, problemFiles);

        Problem p = new Problem(problemFiles);
        Map<Path, String> submissionFiles = new TreeMap<>();
        for (Map.Entry<Path, byte[]> entry : p.getSolutionFiles().entrySet()) 
            submissionFiles.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));          
        for (Map.Entry<Path, byte[]> entry : p.getInputFiles().entrySet()) 
            submissionFiles.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));          

        Properties metaData = new Properties();
        Plan plan = new Main().run(submissionFiles, problemFiles, "html", metaData, resourceLoader);
        if (!isParametric)
            plan.writeSolutionOutputs(problemFiles);
        saveProblem("ext", problem, originalProblemFiles);
        return plan.getReport().getText(); 
    }
    
    public byte[] signZip(Map<Path, byte[]> contents) throws IOException {  
        if (signer == null) return Util.zip(contents);
        Path tempFile = Files.createTempFile(null, ".zip");
        try (OutputStream fout = Files.newOutputStream(tempFile);
             ZipOutputStream zout = new ZipOutputStream(fout);
             ZipFile in = new ZipFile(tempFile.toFile());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map.Entry<Path, byte[]> entry : contents.entrySet()) {
               ZipEntry ze = new ZipEntry(entry.getKey().toString());
               zout.putNextEntry(ze);
