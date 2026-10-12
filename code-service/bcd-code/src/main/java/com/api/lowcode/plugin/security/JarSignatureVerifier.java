package com.api.lowcode.plugin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
@Component
public class JarSignatureVerifier {

    @Value("${lowcode.plugin.signature.required:false}")
    private boolean signatureRequired;

    public boolean isSigned(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile, false)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA")
                        || name.endsWith(".DSA"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void verify(File jarFile) throws Exception {
        if (!signatureRequired) {
            log.debug("[JarSignature] 签名校验未启用，跳过: {}", jarFile.getName());
            return;
        }
        boolean signed = isSigned(jarFile);
        if (!signed) {
            throw new SecurityException("jar 文件未签名: " + jarFile.getName());
        }
        try (JarFile jar = new JarFile(jarFile, true)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] buf = new byte[8192];
                    while (is.read(buf) != -1) {
                    }
                }
            }
        }
        log.info("[JarSignature] 签名校验通过: {}", jarFile.getName());
    }
}