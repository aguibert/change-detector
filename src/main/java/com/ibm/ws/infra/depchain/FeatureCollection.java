package com.ibm.ws.infra.depchain;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureCollection {

    private final String WLP_DIR;
    private final Map<String, Feature> features = new HashMap<>();

    public FeatureCollection(String wlpDir) {
        WLP_DIR = wlpDir;
        File featureDir = new File(WLP_DIR + "/lib/features");
        if (!featureDir.exists() || !featureDir.isDirectory())
            throw new IllegalArgumentException("WLP_DIR did not exist: " + WLP_DIR);
        for (File f : featureDir.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".mf"))
                try {
                    Feature feature = new Feature(f.getAbsolutePath());
                    features.put(feature.getSymbolicName(), feature);
                } catch (IOException ex) {
                    // "Should Never Happen"(TM)
                    ex.printStackTrace();
                }
        }
    }

    public Set<String> publicOnly(Set<String> fList) {
        fList = fList.stream().filter((f) -> features.get(f).isPublic()).collect(Collectors.toSet());
        Set<String> result = new HashSet<>();
        for (String f : fList)
            result.add(features.get(f).getShortName());
        return result;
    }

    public Set<String> getFeaturesUsingBundle(String bundle, Set<String> featureSet) {
        searchFeaturesUsingBundle(bundle, featureSet);
        for (String f : new HashSet<>(featureSet))
            searchFeaturesUsingFeature(f, featureSet);
        if (featureSet.isEmpty())
            throw new IllegalStateException("No features are using bundle " + bundle);
        return featureSet;
    }

    public Set<String> getFeaturesUsingFeature(String feature, Set<String> featureSet) {
        return searchFeaturesUsingFeature(feature, featureSet);
    }

    private Set<String> searchFeaturesUsingBundle(String bundle, Set<String> featureSet) {
        for (Feature f : features.values())
            if (f.getBundles().contains(bundle))
                featureSet.add(f.getSymbolicName());
        return featureSet;
    }

    private Set<String> searchFeaturesUsingFeature(String feature, Set<String> featureSet) {
        for (Feature f : features.values()) {
            String curFeature = f.getSymbolicName();
            if (!featureSet.contains(curFeature) && f.getEnabledFeatures().contains(feature)) {
                featureSet.add(curFeature);
                searchFeaturesUsingFeature(curFeature, featureSet);
            }
        }
        return featureSet;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("WLP_DIR=" + WLP_DIR);
        for (Feature f : features.values())
            sb.append("\n").append(f.toString());
        return sb.toString();
    }

}
