package com.ibm.ws.infra.depchain;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.aries.util.manifest.ManifestProcessor;

public class Feature {

    private final Attributes rawAttrs;
    private final String shortName;
    private final String symbolicName;

    private final Set<String> enablesFeatures = new HashSet<>();
    private final Set<String> bundles = new HashSet<>();

    public Feature(String manifestFile) throws FileNotFoundException, IOException {
        Manifest mf = ManifestProcessor.parseManifest(new FileInputStream(manifestFile));
        rawAttrs = mf.getMainAttributes();
        shortName = rawAttrs.getValue("IBM-ShortName");
        symbolicName = rawAttrs.getValue("Subsystem-SymbolicName");
        if (symbolicName == null || symbolicName.isEmpty())
            throw new IllegalArgumentException("Empty Subsystem-SymbolicName for manifest file: " + manifestFile);
    }

    public String getShortName() {
        return shortName;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public List<String> getEnabledFeatures() {
        return enables;
    }

    public List<String> getInclude() {
        return includes;
    }

//    (&amp;(type=osgi.subsystem.feature)(osgi.identity=com.ibm.websphere.appserver.transaction-1.2))</autoProvision>
//    (&amp;(type=osgi.subsystem.feature)(osgi.identity=com.ibm.websphere.appserver.iioptransport-1.0))</autoProvision>
    public boolean isProvisioned(Map<String, Feature> featureMap, Set<String> installedFeatures) {
        if (isProvisioned != null)
            return isProvisioned;
        if (type != Type.AUTO_FEATURE) {
            isProvisioned = Boolean.TRUE;
            return isProvisioned;
        }

        // Must be an auto feature, check if required features are enabled
        for (String conditionFeature : autoProvision) {
            boolean anyOf = conditionFeature.contains("|");
            boolean isProvisioned = !anyOf;
            for (String identity : conditionFeature.split("osgi.identity=")) {
                int trimAt = identity.indexOf(')');
                if (trimAt < 1)
                    continue;
                identity = identity.substring(0, trimAt);
                if (Pattern.matches("[\\-\\w\\.]+", identity)) {
                    boolean installed = installedFeatures.contains(identity.toLowerCase());
                    if (anyOf)
                        isProvisioned |= installed;
                    else
                        isProvisioned &= installed;
                }
            }
            if (!isProvisioned)
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "{ " + (shortName == null ? "" : ("shortName=" + shortName + ", ")) +
               "symbolicName=" + symbolicName +
               ", enables=" + enables +
               ", includes=" + includes + '}';
    }
}