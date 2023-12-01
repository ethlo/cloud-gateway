package com.ethlo.http.predicates;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import com.ethlo.qjc.Compiler;
import com.ethlo.qjc.IoUtil;
import com.ethlo.qjc.java.JavaCompiler;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

@Component
public class JavaRoutePredicateFactory extends AbstractRoutePredicateFactory<JavaRoutePredicateFactory.Config>
{
    public JavaRoutePredicateFactory()
    {
        super(JavaRoutePredicateFactory.Config.class);
    }

    @Override
    public Predicate<ServerWebExchange> apply(JavaRoutePredicateFactory.Config config)
    {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix()))
        {
            final Path sourcePath = fs.getPath("/source");
            final Path targetPath = fs.getPath("/target");

            Files.createDirectory(sourcePath);
            Files.createDirectory(targetPath);

            final String className = config.getClassName();
            try (final URLClassLoader classLoader = new URLClassLoader(new URL[]{IoUtil.toURL(targetPath)}, getClass().getClassLoader()))
            {
                final Compiler javaCompiler = new JavaCompiler();
                Files.writeString(sourcePath.resolve(className), config.getExpression());
                javaCompiler.compile(Set.of(sourcePath), targetPath);
                try
                {
                    return (Predicate<ServerWebExchange>) classLoader.loadClass(className).getDeclaredConstructor().newInstance();
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

    @Validated
    public static class Config
    {
        private final String className;
        private final String expression;

        public Config(final String className, final String expression)
        {
            this.className = className;
            this.expression = expression;
        }

        public String getExpression()
        {
            return expression;
        }

        public String getClassName()
        {
            return className;
        }
    }
}