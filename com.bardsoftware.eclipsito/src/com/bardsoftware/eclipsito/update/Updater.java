// Copyright (C) 2019 BarD Software
package com.bardsoftware.eclipsito.update;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author dbarashev@bardsoftware.com
 */
public class Updater {

  private final List<File> updateLayerStores;

  public Updater(Collection<File> updateLayerStores) {
    assert updateLayerStores != null && !updateLayerStores.isEmpty(): "Empty list of update layer stores";
    this.updateLayerStores = new ArrayList(updateLayerStores);
  }

  public CompletableFuture<List<UpdateMetadata>> getUpdateMetadata(String updateUrl) {
    HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(updateUrl)).build();
    return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
      try {
        return parseUpdates(resp.body());
      } catch (JsonParserException e) {
        throw new CompletionException(e);
      }
    });
  }

  private List<UpdateMetadata> parseUpdates(String json) throws JsonParserException {
    List<UpdateMetadata> result = new ArrayList<>();
    JsonArray allUpdates = JsonParser.array().from(json);
    for (int i = 0; i < allUpdates.size(); i++) {
      JsonObject update = allUpdates.getObject(i);
      if (update.has("version") && update.has("url")) {
        result.add(new UpdateMetadata(
            update.getString("version"),
            update.getString("url"),
            update.getString("description", "")
        ));
      }
    }
    return result;
  }

  public CompletableFuture<File> installUpdate(UpdateMetadata updateMetadata, UpdateProgressMonitor monitor) throws IOException {
    DownloadWorker updateInstaller = new DownloadWorker(getUpdateLayerStore());
    return updateInstaller.downloadUpdate(updateMetadata.url, monitor);
  }

  private File getUpdateLayerStore() throws IOException {
    return this.updateLayerStores.stream()
        .filter(file -> file.exists() && file.isDirectory() && file.canWrite())
        .findFirst().orElseThrow(() -> new IOException("Cannot find writable directory for installing update"));
  }
}