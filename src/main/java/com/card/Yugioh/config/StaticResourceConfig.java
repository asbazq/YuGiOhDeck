package com.card.Yugioh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

  @Value("${card.image.save-path}")
  private String savePathString;

  @Value("${card.image.small.save-path}")
  private String saveSmallPathString;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 경로 → 절대경로 → URI → String 변환 (file:/.../ 로 끝에 / 포함)
        String largeLocation = ensureTrailingSlash(Paths.get(savePathString)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString());   // 예: file:/var/app/images/

        String smallLocation = ensureTrailingSlash(Paths.get(saveSmallPathString)
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString());   // 예: file:/var/app/images/small/

        registry.addResourceHandler("/images/small/**")
            .addResourceLocations(smallLocation)
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
            .resourceChain(true)
            .addResolver(new PathResourceResolver());

        registry.addResourceHandler("/images/**")
            .addResourceLocations(largeLocation) // ← 끝에 / 포함된 file: URL
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic()) // CacheControl: HTTP 캐시 정책을 선언하는 객체로 
            .resourceChain(true)                                           // 브라우저/CDN에게 캐시 기간/범위를 알려주는 헤더를 생성.
            .addResolver(new PathResourceResolver());


    }
    
    private static String ensureTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
