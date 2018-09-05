package com.ibm.ws.infra.depchain;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.aries.util.manifest.ManifestHeaderProcessor;
import org.apache.aries.util.manifest.ManifestHeaderProcessor.GenericMetadata;
import org.apache.aries.util.manifest.ManifestHeaderProcessor.NameValuePair;
import org.apache.aries.util.manifest.ManifestProcessor;

public class Feature {

    private final Attributes rawAttrs;
    private final String shortName;
    private final String symbolicName;

    private final Set<String> enabledByFeatures;
    private final Set<String> enablesFeatures = new HashSet<>();
    private final Set<String> bundles = new HashSet<>();
    private final Set<String> files = new HashSet<>();

    public Feature(String manifestFile) throws IOException {
        Manifest mf = ManifestProcessor.parseManifest(new FileInputStream(manifestFile));
        rawAttrs = mf.getMainAttributes();
        shortName = rawAttrs.getValue("IBM-ShortName");
        NameValuePair bsn = ManifestHeaderProcessor.parseBundleSymbolicName(rawAttrs.getValue("Subsystem-SymbolicName"));
        symbolicName = bsn.getName();
        if (symbolicName == null || symbolicName.isEmpty())
            throw new IllegalArgumentException("Empty Subsystem-SymbolicName for manifest file: " + manifestFile);

        List<GenericMetadata> provisionCapability = ManifestHeaderProcessor.parseCapabilityString(rawAttrs.getValue("IBM-Provision-Capability"));
        if (provisionCapability == null) {
            enabledByFeatures = null;
        } else {
            enabledByFeatures = new HashSet<>();
            if (manifestFile.contains("beanValidationCDI-2.0")) {
                System.out.println("@AGG found auto-feature: " + manifestFile);
                for (GenericMetadata metadata : provisionCapability) {
                    String filter = metadata.getDirectives().get("filter");
                    // TODO LEFTOFF parse auto features
                    if (filter != null && filter.contains("osgi.identity=")) {
                        String f = filter.substring(filter.indexOf("osgi.identity="), endIndex)
                    }
                    System.out.println("  " + metadata.getDirectives());
                }
            }
        }

        // Parse included bundles and features
        Map<String, Map<String, String>> content = ManifestHeaderProcessor.parseImportString(rawAttrs.getValue("Subsystem-Content"));
        for (Entry<String, Map<String, String>> e : content.entrySet()) {
            String key = e.getKey();
            String type = e.getValue().get("type");
            if (type == null)
                bundles.add(key);
            else if ("osgi.subsystem.feature".equals(type) || "jar".equals(type))
                enablesFeatures.add(key);
            else if ("file".equals(type))
                files.add(key);
            else
                throw new IllegalStateException("Found unknown content: type=" + type + "  " + e.getKey() + "   " + e.getValue());
        }
    }

    public boolean isPublic() {
        // Don't consider install bundles like 'com.ibm.websphere.appserver.webProfile8Bundle' to be public features
        return shortName != null && !shortName.endsWith("Bundle");
    }

    public boolean isAutoFeature() {
        return enabledByFeatures != null;
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
            return "bsn=" + symbolicName + "\n  features=" + enablesFeatures + "\n  bundles= " + bundles;
        return "{ " + (shortName == null ? "" : ("shortName=" + shortName + ", ")) +
               "symbolicName=" + symbolicName +
               ", enables=" + enablesFeatures +
               ", bundles=" + bundles + '}';
    }
}