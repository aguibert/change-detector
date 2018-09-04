package com.ibm.ws.infra.depchain;

import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

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

    private static final boolean DEBUG = true;
    public static final String OL_ROOT = "C:\\dev\\proj\\open-liberty\\dev\\";
    public static final String WLP_DIR = OL_ROOT + "\\build.image\\wlp";

    public static void main(String args[]) throws Exception {
        String prURL = "https://github.com/OpenLiberty/open-liberty/pull/4942.diff";
        System.out.println("FATs to run: " + new ChangeDetector().getFatsToRun(prURL));
    }

    public Set<String> getFatsToRun(String prURL) throws Exception {
        Set<String> fatsToRun = new HashSet<String>();

        // Get the changed files from the pull request diff URL
        Set<String> modifiedFiles = getModifiedFiles(prURL);
        Set<String> modifiedBundles = new HashSet<>();
        Set<String> modifiedFeatureManifests = new HashSet<>();

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
                    modifiedFeatureManifests.add(f.substring(f.lastIndexOf('/') + 1, f.lastIndexOf('.')));
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
        for (String manifest : modifiedFeatureManifests)
            System.out.println("  " + manifest);

        Feature f = new Feature(WLP_DIR + "/lib/features/com.ibm.websphere.appserver.ejbCore-1.0.mf");

        return fatsToRun;
    }

    public Set<String> getModifiedFiles(String pullRequestURL) {
        Set<String> modifiedFiles = new HashSet<>();

        if (DEBUG) {
            modifiedFiles.add("dev/com.ibm.ws.anno/src/com/ibm/ws/anno/info/internal/InfoVisitor.java");
            modifiedFiles.add("dev/com.ibm.ws.ejbcontainer.core/src/com/ibm/ws/metadata/ejb/ByteCodeMetaData.java");
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
        try (Scanner s = new Scanner(responseStr)) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.startsWith("--- a")) {
                    String f = line.substring(6);
                    if (DEBUG)
                        System.out.println("Modified file: " + f);
                    modifiedFiles.add(f);
                }
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
            fPath.contains("/test/"))
            return FileType.UNIT_BVT_TEST;
        if (fPath.contains("_fat"))
            return FileType.FAT_TEST;
        if (fPath.contains("/ant_") ||
            fPath.contains("/build.") ||
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
