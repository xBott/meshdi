package me.bottdev.meshdi.moduleit.core.utils;

import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class InMemoryOutputFile extends SimpleJavaFileObject {

    private final String path;
    private final Map<String, byte[]> sink;
    private volatile byte[] content;

    InMemoryOutputFile(String path, Kind kind, Map<String, byte[]> sink) {
        super(URI.create("mem:///" + path), kind);
        this.path = path;
        this.sink = sink;
    }

    @Override
    public OutputStream openOutputStream() {
        return new ByteArrayOutputStream() {
            @Override public void close() throws IOException {
                super.close();
                content = toByteArray();
                sink.put(path, content);
            }
        };
    }

    @Override
    public InputStream openInputStream() throws IOException {
        if (content == null) {
            throw new FileNotFoundException("No content written yet for " + path);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        if (content == null) {
            throw new FileNotFoundException("No content written yet for " + path);
        }
        return new String(content, StandardCharsets.UTF_8);
    }
}