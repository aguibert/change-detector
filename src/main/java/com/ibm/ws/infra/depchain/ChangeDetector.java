package com.ibm.ws.infra.depchain;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.stream.JsonParser;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

public class ChangeDetector {

    public static enum FileType {
        PRODUCT,
        PRODUCT_FEATURE,
        UNIT_BVT_TEST,
        FAT_TEST,
        INFRA,
        UNKNOWN
    }

    public static final boolean CACHE_DIFF = true;
    public static final boolean LOCAL_ONLY = false;
    public static final boolean DEBUG = true;
    public static final String WLP_DIR = "C:\\dev\\proj\\open-liberty\\dev\\build.image\\wlp";
    //public static final String WLP_DIR = "/Users/aguibert/dev/git/WS-CD-Open/dev/build.image/wlp";
    private static final String GIT_FILECHANGE_TOKEN = "diff --git a";

    public static void main(String args[]) throws Exception {
        //String prURL = "https://github.com/OpenLiberty/open-liberty/pull/4942.diff";
        String prURL = "https://github.com/OpenLiberty/open-liberty/pull/4951.diff";
        // TODO auth for GHE
        //String prURL = "https://github.ibm.com/was-liberty/WS-CD-Open/pull/13165.diff";
        Set<String> fatsToRun = new ChangeDetector().getFatsToRun(prURL);
        System.out.println("Fats to run: ");
        for (String fat : fatsToRun.stream().sorted().collect(Collectors.toList()))
            System.out.println("  " + fat);
    }

    public Set<String> getFatsToRun(String prURL) throws Exception {
        Set<String> fatsToRun = new HashSet<String>();

        // Get the changed files from the pull request diff URL
        Set<String> modifiedFiles = getModifiedFiles(prURL);
        Set<String> modifiedBundles = new HashSet<>();
        Set<String> modifiedFeatures = new HashSet<>();

        // Sort out the modified files
        for (String f : modifiedFiles) {
            FileType fType = getFileType(f);
            switch (fType) {
                case UNIT_BVT_TEST:
                    break;
                case FAT_TEST:
                    fatsToRun.add(getProjectName(f));
                    break;
                case PRODUCT:
                    modifiedBundles.add(getProjectName(f));
                    break;
                case PRODUCT_FEATURE:
                    modifiedFeatures.add(f.substring(f.lastIndexOf('/') + 1, f.lastIndexOf('.')));
                    break;
                case INFRA:
                    System.out.println("Run everything because we found an infra file: " + f);
                    return runEverything();
                case UNKNOWN:
                default:
                    System.out.println("Run everything because we found an unknown file: " + f);
                    return runEverything();
            }
        }

        System.out.println("Modified bundles:");
        for (String bundle : modifiedBundles)
            System.out.println("  " + bundle);
        System.out.println("Modified feature manifests: ");
        for (String manifest : modifiedFeatures)
            System.out.println("  " + manifest);

        FeatureCollection features = new FeatureCollection(WLP_DIR);
        try (PrintWriter log = new PrintWriter("target/out.log")) {
            log.print(features.toString());
        }

        Set<String> testedFeautres = new HashSet<>();
//        features.getFeaturesUsingBundle("com.ibm.ws.anno", testedFeautres);
        for (String bundle : modifiedBundles)
            features.getFeaturesUsingBundle(bundle, testedFeautres);
        for (String feature : modifiedFeatures)
            features.getFeaturesUsingFeature(feature, testedFeautres);
        testedFeautres = features.publicOnly(testedFeautres);
        System.out.println("Features impacted by this PR: " + testedFeautres);

        return getFATsToRun(testedFeautres);
    }

    public Set<String> getFATsToRun(Set<String> featureSet) {
        try {
            JsonParser parser = Json.createParser(new FileInputStream("src/main/resources/overall-fat-feature-deps.json"));
            parser.next();
            JsonObject fatMap = parser.getObject();
            Set<String> fatsToRun = new HashSet<>();
            for (String feature : featureSet) {
                JsonArray fatsForFeature = fatMap.getJsonArray(feature);
                if (fatsForFeature != null)
                    for (JsonString fat : fatsForFeature.getValuesAs(JsonString.class))
                        fatsToRun.add(fat.getString());
                System.out.println("FATs testing feature " + feature + " are: " + fatMap.getJsonArray(feature));
            }
            if (fatsToRun.isEmpty())
                return Collections.singleton("all");
            return fatsToRun;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Collections.singleton("all");
        }
    }

    public Set<String> getModifiedFiles(String pullRequestURL) {
        Set<String> modifiedFiles = new HashSet<>();

        if (LOCAL_ONLY) {
            modifiedFiles.add("dev/com.ibm.ws.anno/src/com/ibm/ws/anno/info/internal/InfoVisitor.java");
            // TODO: handle projects that don't produce bundles
            // modifiedFiles.add("dev/com.ibm.ws.ejbcontainer.core/src/com/ibm/ws/metadata/ejb/ByteCodeMetaData.java");
            modifiedFiles.add("dev/com.ibm.ws.ejbcontainer/src/com/ibm/ws/metadata/ejb/ByteCodeMetaData.java");
            modifiedFiles.add("dev/com.ibm.ws.monitor/src/com/ibm/ws/monitor/internal/MonitoringProxyActivator.java");
            modifiedFiles.add("dev/com.ibm.ws.monitor/src/com/ibm/ws/monitor/internal/bci/ClassAvailableHookClassAdapter.java");
            modifiedFiles.add("dev/com.ibm.ws.ras.instrument/src/com/ibm/ws/ras/instrument/internal/bci/DeferConstructorProcessingMethodAdapter.java");
            modifiedFiles.add("dev/com.ibm.ws.ras.instrument/test/com/ibm/ws/ras/instrument/internal/bci/DeferConstructorProcessingMethodAdapter.java");
            modifiedFiles.add("dev/com.ibm.ws.ras.instrument_test/test/com/ibm/ws/ras/RasTransformTest.java");
            modifiedFiles.add("dev/com.ibm.ws.request.probes/src/com/ibm/ws/request/probe/bci/internal/RequestProbeClassVisitor.java");
//            modifiedFiles.add("dev/fattest.simplicity/bnd.bnd");
            modifiedFiles.add("dev/com.ibm.websphere.appserver.features/visibility/private/com.ibm.websphere.appserver.ejbCore-1.0.feature");
            return modifiedFiles;
        }

        System.out.println("Parsing modified files for PR: " + pullRequestURL);
        HttpClient client = HttpClientBuilder.create().build();
        HttpGet req = new HttpGet(pullRequestURL);
        String responseStr = null;
        try {
            HttpResponse response = client.execute(req);
            responseStr = EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            modifiedFiles.add("ERROR");
            e.printStackTrace();
            return modifiedFiles;
        }
        System.out.println("Raw modified files:");
        try (Scanner s = new Scanner(responseStr)) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.startsWith(GIT_FILECHANGE_TOKEN)) {
                    String f = line.substring(GIT_FILECHANGE_TOKEN.length() + 1, line.indexOf(" b/"));
                    System.out.println("  " + f);
                    modifiedFiles.add(f);
                }
            }
        }

        if (modifiedFiles.isEmpty())
            throw new IllegalStateException("No modified files!  Something must have gone wrong reading this URL: " + pullRequestURL);

        if (CACHE_DIFF) {
            String diffFile = pullRequestURL.substring(pullRequestURL.lastIndexOf('/'), pullRequestURL.length());
            try (PrintWriter pw = new PrintWriter("src/main/resources/" + diffFile)) {
                for (String f : modifiedFiles.stream().sorted().collect(Collectors.toList()))
                    pw.println(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return modifiedFiles;
    }

    private Set<String> runEverything() {
        Set<String> s = new HashSet<>();
        s.add("all");
        return s;
    }

    private String getProjectName(String file) {
        if (file == null || !file.contains("dev/"))
            return null;
        return file.substring(4, file.indexOf('/', 4));
    }

    private static FileType getFileType(String fPath) {
        if (fPath == null)
            return FileType.UNKNOWN;
        if (fPath.contains("_test") ||
            fPath.contains("_bvt") ||
            fPath.contains("/test/") ||
            (fPath.endsWith(".properties") && fPath.contains("com.ibm.websphere.appserver.features")))
            return FileType.UNIT_BVT_TEST;
        if (fPath.contains("_fat"))
            return FileType.FAT_TEST;
        if (fPath.contains("/ant_") ||
            (fPath.contains("/build.") && !fPath.endsWith(".gradle") && !fPath.endsWith(".xml")) ||
            fPath.contains("/fattest.simplicity") ||
            fPath.contains("/cnf/"))
            return FileType.INFRA;
        if (fPath.endsWith(".feature") ||
            fPath.endsWith(".mf"))
            return FileType.PRODUCT_FEATURE;
        if (fPath.contains("dev/"))
            return FileType.PRODUCT;

        System.out.println("WARNING: Found unknown file: " + fPath);
        return FileType.UNKNOWN;
    }

}
