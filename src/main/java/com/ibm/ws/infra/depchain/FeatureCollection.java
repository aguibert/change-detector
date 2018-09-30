package com.ibm.ws.infra.depchain;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureCollection {

    private final Map<String, Feature> knownFeatures;

    public FeatureCollection(String wlpDir) {
        Map<String, Feature> knownFeaturesWritable = new HashMap<>();
        knownFeaturesWritable.putAll(discoverFeatureFiles(wlpDir + "/lib/features"));
        knownFeaturesWritable.putAll(discoverFeatureFiles(wlpDir + "/lib/platform"));
        knownFeatures = Collections.unmodifiableMap(knownFeaturesWritable);
    }

    public Feature get(String featureSymbolicName) {
        return knownFeatures.get(featureSymbolicName);
    }

    public Feature getPublic(String featureShortName) {
        // Most of the time this optimization will work getting feature symbolic name
        Feature f = knownFeatures.get("com.ibm.websphere.appserver." + featureShortName);
        if (f != null)
            return f;
        for (Feature knownFeature : knownFeatures.values())
            if (knownFeature.isPublic() && knownFeature.getShortName().equalsIgnoreCase(featureShortName))
                return knownFeature;
        return null;
    }

    public Set<String> filterPublicOnly(Set<String> fList) {
        fList = fList.stream().filter((f) -> knownFeatures.get(f).isPublic()).collect(Collectors.toSet());
        Set<String> result = new HashSet<>();
        for (String f : fList)
            result.add(knownFeatures.get(f).getShortName());
        return result;
    }

    public Set<String> filterAutoOnly(Set<String> fList) {
        return fList.stream().filter((f) -> knownFeatures.get(f).isAutoFeature()).collect(Collectors.toSet());
    }

    public void addFeaturesUsingBundle(String bundle, Set<String> featureSet) {
        searchFeaturesUsingBundle(bundle, featureSet);
        for (String f : new HashSet<>(featureSet))
            searchFeaturesUsingFeature(f, featureSet);
        if (featureSet.isEmpty())
            throw new IllegalStateException("No features are using bundle " + bundle);
    }

    public void addFeaturesUsingFeature(String feature, Set<String> featureSet) {
        searchFeaturesUsingFeature(feature, featureSet);
        featureSet.add(feature);
    }

    public void addEnabledAutoFeatures(Set<String> featureSet) {
        for (Feature f : knownFeatures.values()) {
            if (f.isAutoFeature() && f.isCapabilitySatisfied(featureSet)) {
                System.out.println("@AGG adding auto feature: " + f.getSymbolicName());
                featureSet.add(f.getSymbolicName());
            }
        }
    }

    private Set<String> searchFeaturesUsingBundle(String bundle, Set<String> featureSet) {
        for (Feature f : knownFeatures.values())
            if (f.getBundles().contains(bundle))
                featureSet.add(f.getSymbolicName());
        return featureSet;
    }

    private Set<String> searchFeaturesUsingFeature(String feature, Set<String> featureSet) {
        for (Feature f : knownFeatures.values()) {
            String curFeature = f.getSymbolicName();
            if (!featureSet.contains(curFeature) && f.getEnabledFeatures().contains(feature)) {
                featureSet.add(curFeature);
                searchFeaturesUsingFeature(curFeature, featureSet);
            }
        }
        return featureSet;
    }

    private Map<String, Feature> discoverFeatureFiles(String dir) {
        File featureDir = new File(dir);
        if (!featureDir.exists() || !featureDir.isDirectory())
            throw new IllegalArgumentException("Directory did not exist: " + dir);
        Map<String, Feature> knownFeaturesWritable = new HashMap<>();
        for (File f : featureDir.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".mf"))
                try {
                    Feature feature = new Feature(f.getAbsolutePath());
                    knownFeaturesWritable.put(feature.getSymbolicName(), feature);
                } catch (IOException ex) {
                    // "Should Never Happen"(TM)
                    ex.printStackTrace();
                }
        }
        return knownFeaturesWritable;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Feature f : knownFeatures.values())
            sb.append("\n").append(f.toString());
        return sb.toString();
    }

}
