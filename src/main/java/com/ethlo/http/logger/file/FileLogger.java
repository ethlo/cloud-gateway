package com.ethlo.http.logger.file;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import com.ethlo.http.logger.BodyContentRepository;
import com.ethlo.http.logger.HttpLogger;
import com.ethlo.http.logger.MetadataContentRepository;
import com.ethlo.http.model.PayloadProvider;
import com.ethlo.http.model.WebExchangeDataProvider;

public class FileLogger implements HttpLogger
{
    private static final Logger accessLogLogger = LoggerFactory.getLogger("access-log");

    private final MetadataContentRepository metadataContentRepository;
    private final BodyContentRepository bodyContentRepository;
    private final AccessLogTemplateRenderer accessLogTemplateRenderer;

    public FileLogger(MetadataContentRepository metadataContentRepository, final BodyContentRepository bodyContentRepository, final AccessLogTemplateRenderer accessLogTemplateRenderer)
    {
        this.metadataContentRepository = metadataContentRepository;
        this.bodyContentRepository = bodyContentRepository;
        this.accessLogTemplateRenderer = accessLogTemplateRenderer;
    }

    @Override
    public void accessLog(final WebExchangeDataProvider dataProvider)
    {
        final Map<String, Object> metaMap = dataProvider.asMetaMap();
        accessLogLogger.info(accessLogTemplateRenderer.render(metaMap));

        metadataContentRepository.save(dataProvider.getRequestId(), metaMap);

        final Optional<Resource> reqResource = dataProvider.getRequestPayload().map(PayloadProvider::data).map(InputStreamResource::new);
        reqResource.ifPresent(res -> bodyContentRepository.saveRequestBody(dataProvider.getRequestId(), res));

        final Optional<Resource> resResource = dataProvider.getResponsePayload().map(PayloadProvider::data).map(InputStreamResource::new);
        resResource.ifPresent(res -> bodyContentRepository.saveResponseBody(dataProvider.getRequestId(), res));
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " - pattern='" + accessLogTemplateRenderer.getPattern() + "'";
    }
}
