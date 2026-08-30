package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.exceptions.library.PomParseException;
import me.bottdev.meshdi.moduleit.api.library.xml.XmlNode;
import me.bottdev.meshdi.moduleit.api.library.xml.XmlParser;

import java.nio.file.Path;

public class MavenMetadataParser {

    public static String resolveSnapshotVersion(Path metadataFile, String baseVersion) throws PomParseException {
        try {
            XmlNode root = XmlParser.parse(metadataFile);

            XmlNode versioning = root.child("versioning");
            if (versioning != null) {
                XmlNode snapshot = versioning.child("snapshot");
                if (snapshot != null) {
                    String timestamp = snapshot.text("timestamp");
                    String buildNumber = snapshot.text("buildNumber");
                    
                    if (timestamp != null && buildNumber != null && baseVersion.endsWith("-SNAPSHOT")) {
                        return baseVersion.replace("-SNAPSHOT", "-" + timestamp + "-" + buildNumber);
                    }
                }
            }

            return baseVersion;

        } catch (Exception ex) {
            throw new PomParseException("Failed to parse maven-metadata.xml: " + metadataFile, ex);
        }
    }
}
