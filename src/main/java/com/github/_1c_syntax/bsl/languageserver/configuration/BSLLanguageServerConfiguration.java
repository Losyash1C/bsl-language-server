/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.configuration.codelens.CodeLensOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.BSLDiagnosticsOptions;
import com.github._1c_syntax.bsl.languageserver.configuration.documentlink.DocumentLinkOptions;
import com.github._1c_syntax.ls_core.configuration.LSPFeature;
import com.github._1c_syntax.ls_core.configuration.Language;
import com.github._1c_syntax.ls_core.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.ls_core.configuration.watcher.LanguageServerConfigurationChangeEvent;
import com.github._1c_syntax.ls_core.configuration.watcher.LanguageServerConfigurationFileChangeEvent;
import com.github._1c_syntax.utils.Absolute;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;

/**
 * Корневой класс конфигурации BSL Language Server.
 * <p>
 * В обычном режиме работы провайдеры и прочие классы могут рассчитывать на единственность объекта конфигурации
 * и безопасно сохранять ссылку на конфигурацию или ее части.
 */
@Data
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@AllArgsConstructor(onConstructor = @__({@JsonCreator(mode = JsonCreator.Mode.DISABLED)}))
@NoArgsConstructor
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class BSLLanguageServerConfiguration implements LanguageServerConfiguration {

  private static final Pattern searchConfiguration = Pattern.compile("Configuration\\.(xml|mdo)$");

  private Language language = Language.DEFAULT_LANGUAGE;

  @JsonProperty("diagnostics")
  @Setter(value = AccessLevel.NONE)
  private BSLDiagnosticsOptions diagnosticsOptions = new BSLDiagnosticsOptions();

  @JsonProperty("codeLens")
  @Setter(value = AccessLevel.NONE)
  private CodeLensOptions codeLensOptions = new CodeLensOptions();

  @JsonProperty("documentLink")
  @Setter(value = AccessLevel.NONE)
  private DocumentLinkOptions documentLinkOptions = new DocumentLinkOptions();

  @Nullable
  private File traceLog;

  @Nullable
  private Path configurationRoot;

  @JsonIgnore
  @Setter(value = AccessLevel.NONE)
  private File configurationFile = new File(".bsl-language-server.json");

  @JsonIgnore
  @Getter(value = AccessLevel.NONE)
  private ApplicationEventPublisher applicationEventPublisher;

  /**
   * Доступные фичи language server
   */
  @JsonIgnore
  private Set<LSPFeature> lspFeatures;

  @Override
  public void update(File configurationFile) {
    if (!configurationFile.exists()) {
      return;
    }

    BSLLanguageServerConfiguration configuration;

    var mapper = new ObjectMapper();
    mapper.enable(ACCEPT_CASE_INSENSITIVE_ENUMS);

    try {
      configuration = mapper.readValue(configurationFile, BSLLanguageServerConfiguration.class);
    } catch (IOException e) {
      LOGGER.error("Can't deserialize configuration file", e);
      return;
    }

    this.configurationFile = configurationFile;
    notifyConfigurationFileChanged();

    copyPropertiesFrom(configuration);
    notifyConfigurationChanged();
  }

  @Override
  public void reset() {
    copyPropertiesFrom(new BSLLanguageServerConfiguration());
    notifyConfigurationFileChanged();
    notifyConfigurationChanged();
  }

  @Override
  public void addLSPFeature(LSPFeature lspFeature) {
    if (lspFeatures == null) {
      lspFeatures = new HashSet<>();
    }
    lspFeatures.add(lspFeature);
  }

  @Override
  public boolean isLSPFeature(LSPFeature lspFeature) {
    if (lspFeatures == null) {
      return false;
    }
    return lspFeatures.contains(lspFeature);
  }

  public static Path getCustomConfigurationRoot(BSLLanguageServerConfiguration configuration, Path srcDir) {

    Path rootPath = null;
    Path pathFromConfiguration = configuration.getConfigurationRoot();

    if (pathFromConfiguration == null) {
      rootPath = Absolute.path(srcDir);
    } else {
      // Проверим, что srcDir = pathFromConfiguration или что pathFromConfiguration находится внутри srcDir
      var absoluteSrcDir = Absolute.path(srcDir);
      var absolutePathFromConfiguration = Absolute.path(pathFromConfiguration);
      if (absolutePathFromConfiguration.startsWith(absoluteSrcDir)) {
        rootPath = absolutePathFromConfiguration;
      }
    }

    if (rootPath != null) {
      File fileConfiguration = getConfigurationFile(rootPath);
      if (fileConfiguration != null) {
        if (fileConfiguration.getAbsolutePath().endsWith(".mdo")) {
          rootPath = Optional.of(fileConfiguration.toPath())
            .map(Path::getParent)
            .map(Path::getParent)
            .map(Path::getParent)
            .orElse(null);
        } else {
          rootPath = Optional.of(fileConfiguration.toPath())
            .map(Path::getParent)
            .orElse(null);
        }
      }
    }

    return rootPath;

  }

  private static File getConfigurationFile(Path rootPath) {
    File configurationFile = null;
    List<Path> listPath = new ArrayList<>();
    try (Stream<Path> stream = Files.find(rootPath, 50, (path, basicFileAttributes) ->
      basicFileAttributes.isRegularFile() && searchConfiguration.matcher(path.getFileName().toString()).find())) {
      listPath = stream.collect(Collectors.toList());
    } catch (IOException e) {
      LOGGER.error("Error on read configuration file", e);
    }
    if (!listPath.isEmpty()) {
      configurationFile = listPath.get(0).toFile();
    }
    return configurationFile;
  }

  @SneakyThrows
  private void copyPropertiesFrom(BSLLanguageServerConfiguration configuration) {
    // todo: refactor
    PropertyUtils.copyProperties(this, configuration);
    PropertyUtils.copyProperties(this.codeLensOptions, configuration.codeLensOptions);
    PropertyUtils.copyProperties(this.diagnosticsOptions, configuration.diagnosticsOptions);
    PropertyUtils.copyProperties(this.documentLinkOptions, configuration.documentLinkOptions);
  }

  private void notifyConfigurationFileChanged() {
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationFileChangeEvent(this.configurationFile));
  }

  private void notifyConfigurationChanged() {
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationChangeEvent(this));
  }
}
