package com.ethlo.http.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.ethlo.qjc.Compiler;
import com.ethlo.qjc.java.JavaCompiler;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class JavaCompiledExpressionUtil
{
    private static final Logger logger = LoggerFactory.getLogger(JavaCompiledExpressionUtil.class);

    public static <T> T load(JavaExpressionConfig config, final Class<T> type)
    {
        final String className = "Class" + UUID.randomUUID().toString().replace("-", "");
        final String classExpression = Optional.ofNullable(config.getTemplate())
                .map(tpl -> getClassExpression(new ClassPathResource("java/" + tpl + ".java"), className, config.getExpression()))
                .orElseGet(config::getExpression);

        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix()))
        {
            final Path sourcePath = fs.getPath("source").toAbsolutePath();
            final Path targetPath = fs.getPath("target").toAbsolutePath();

            Files.createDirectory(sourcePath);
            Files.createDirectory(targetPath);

            final URL targetUrl = targetPath.toUri().toURL();
            try (final URLClassLoader classLoader = new URLClassLoader(new URL[]{targetUrl}, JavaCompiledExpressionUtil.class.getClassLoader()))
            {
                final Compiler javaCompiler = new JavaCompiler(classLoader);
                logger.debug("Class content: {}", classExpression);
                Files.writeString(sourcePath.resolve(className + ".java"), classExpression);
                javaCompiler.compile(Set.of(sourcePath), targetPath);
                try
                {
                    return type.cast(classLoader.loadClass(className).getDeclaredConstructor().newInstance());
                }
                catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                       IllegalAccessException | InvocationTargetException e)
                {
                    throw new UncheckedIOException(new IOException("Could not find class:" + className, e));
                }
            }
            catch (IOException exc)
            {
                throw new UncheckedIOException(exc);
            }
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static String getClassExpression(final Resource resource, final String className, final String expression)
    {
        try
        {
            final String tplExpression = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return tplExpression
                    .replace("$className", className)
                    .replace("$expression", expression);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
