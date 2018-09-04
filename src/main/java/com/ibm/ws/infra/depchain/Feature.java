package com.ibm.ws.infra.depchain;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.aries.util.manifest.ManifestHeaderProcessor;
import org.apache.aries.util.manifest.ManifestHeaderProcessor.NameValuePair;
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
        NameValuePair bsn = ManifestHeaderProcessor.parseBundleSymbolicName(rawAttrs.getValue("Subsystem-SymbolicName"));
        symbolicName = bsn.getName();
        if (symbolicName == null || symbolicName.isEmpty())
            throw new IllegalArgumentException("Empty Subsystem-SymbolicName for manifest file: " + manifestFile);

        // TODO auto features

        // Parse included bundles and features
        Map<String, Map<String, String>> content = ManifestHeaderProcessor.parseImportString(rawAttrs.getValue("Subsystem-Content"));
        for (Entry<String, Map<String, String>> e : content.entrySet()) {
            String type = e.getValue().get("type");
            if ("osgi.subsystem.feature".equals(type))
                enablesFeatures.add(e.getKey());
            else
                bundles.add(e.getKey());
        }
    }

    public String getShortName() {
        return shortName;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public Set<String> getEnabledFeatures() {
        return Collections.unmodifiableSet(enablesFeatures);
    }

    public Set<String> getBundles() {
        return Collections.unmodifiableSet(bundles);
    }

    @Override
    public String toString() {
        if (ChangeDetector.DEBUG)
            return symbolicName + "\n  features=" + enablesFeatures + "\n  bundles= " + bundles;
        return "{ " + (shortName == null ? "" : ("shortName=" + shortName + ", ")) +
               "symbolicName=" + symbolicName +
               ", enables=" + enablesFeatures +
               ", bundles=" + bundles + '}';
    }
}