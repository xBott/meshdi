package me.bottdev.meshdi.moduleit.api.library;

import me.bottdev.meshdi.moduleit.api.exceptions.library.PomParseException;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

public class MavenMetadataParser {

    public static String resolveSnapshotVersion(Path metadataFile, String baseVersion) throws PomParseException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            Document doc = dbf.newDocumentBuilder().parse(metadataFile.toFile());
            XmlNode root = new XmlNode(doc.getDocumentElement());

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
