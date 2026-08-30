package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.exceptions.library.PomParseException;

import java.nio.file.Path;
import java.util.*;

public class PomModelParser {

    public static PomModel parseRaw(Path pomPath) throws PomParseException {
        try {
            XmlNode root = XmlParser.parse(pomPath);

            String groupId = root.text("groupId");
            String artifactId = root.text("artifactId");
            String version = root.text("version");

            MavenCoordinate parentCoord = null;
            XmlNode parentEl = root.child("parent");
            if (parentEl != null) {
                String pGroupId = parentEl.text("groupId");
                String pArtifactId = parentEl.text("artifactId");
                String pVersion = parentEl.text("version");
                parentCoord = new MavenCoordinate(pGroupId, pArtifactId, pVersion, null, "pom");
                if (groupId == null) groupId = pGroupId;
                if (version == null) version = pVersion;
            }

            Map<String, String> properties = new LinkedHashMap<>();
            XmlNode propsNode = root.child("properties");
            if (propsNode != null) {
                properties.putAll(propsNode.propertiesAsMap());
            }
            if (version != null) properties.putIfAbsent("project.version", version);
            if (groupId != null) properties.putIfAbsent("project.groupId", groupId);

            List<PomModel.RawManagedDependency> depMgmt = new ArrayList<>();
            XmlNode depMgmtDeps = root.child("dependencyManagement");
            if (depMgmtDeps != null) depMgmtDeps = depMgmtDeps.child("dependencies");
            if (depMgmtDeps != null) {
                for (XmlNode dep : depMgmtDeps.children("dependency")) {
                    depMgmt.add(new PomModel.RawManagedDependency(
                            dep.text("groupId"), dep.text("artifactId"), dep.text("version"),
                            dep.text("scope"), dep.text("type")
                    ));
                }
            }

            List<PomModel.PomDependency> deps = new ArrayList<>();
            XmlNode depsNode = root.child("dependencies");
            if (depsNode != null) {
                for (XmlNode dep : depsNode.children("dependency")) {
                    Set<String> exclusions = new HashSet<>();
                    XmlNode exclNode = dep.child("exclusions");
                    if (exclNode != null) {
                        for (XmlNode excl : exclNode.children("exclusion")) {
                            exclusions.add(excl.text("groupId") + ":" + excl.text("artifactId"));
                        }
                    }
                    deps.add(new PomModel.PomDependency(
                            dep.text("groupId"), dep.text("artifactId"), dep.text("version"),
                            dep.text("scope"), "true".equals(dep.text("optional")),
                            exclusions, null
                    ));
                }
            }

            MavenCoordinate self = new MavenCoordinate(groupId, artifactId, version, null, "jar");

            return new PomModel(self, parentCoord, properties, depMgmt, deps, Map.of(), Map.of());

        } catch (Exception ex) {
            throw new PomParseException("Failed to parse POM: " + pomPath, ex);
        }
    }
}
