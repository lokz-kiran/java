package com.gitcolony.jenkins.gitcolony;

import hudson.model.TaskListener;
import hudson.model.Run;
import hudson.model.Job;
import hudson.model.Result;
import hudson.EnvVars;
import net.sf.json.JSONObject;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.OutputStream;
import java.util.Map;

@hudson.Extension
@SuppressWarnings("rawtypes")
public class Listener extends hudson.model.listeners.RunListener<Run> {
  public Listener() {
    super(Run.class);
  }

  @Override
  public void onCompleted(Run run, TaskListener listener) {
    try {
      notify(run, listener);
    } catch (Throwable error) {
      //error.printStackTrace(listener.error("Gitcolony notification failed"));
      listener.getLogger().println(String.format("Gitcolony notification failed - %s: %s",
                                                 error.getClass().getName(), error.getMessage()));
    }
  }

  protected void notify(Run run, TaskListener listener) throws Throwable {
    // Load URL
    UrlProperty property = (UrlProperty) run.getParent().getProperty(UrlProperty.class);
    if (property == null) { return; }

    String urlText = expandVariables(property.getUrl(), run.getEnvironment(listener));

    URL url;
    try {
      url = new URL(urlText);
      if (!url.getProtocol().startsWith("http")) {
        throw new IllegalArgumentException("Not an http(s) url: " + urlText);
      }
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid url: " + urlText);
    }

    // Build payload JSON
    JSONObject buildStatus = new JSONObject();
    Result  result = run.getResult();
    buildStatus.put("status", result != null ? result.toString() : "UNKNOWN");
    String sha = null;
    try { sha = run.getEnvironment(listener).get("GIT_COMMIT"); } catch (Exception e) {}
    buildStatus.put("sha", sha);
    JSONObject payload = new JSONObject();
    payload.put("build", buildStatus);

    // Send notification
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
    //conn.setConnectTimeout(timeout);
    //conn.setReadTimeout(timeout);

    conn.setDoOutput(true);
    OutputStream output = conn.getOutputStream();
    output.write(payload.toString().getBytes("UTF-8"));
    output.close();

    int code = conn.getResponseCode();
    listener.getLogger().println(String.format("Gitcolony notification sent: %d", code));
  }


  protected String expandVariables(String urlText, EnvVars env) {
    String ret = null;

    for (String part : urlText.split("/")) {
      if (part.startsWith("$$")) {
        part = part.replace("$$", "$");
      } else if (part.startsWith("$")) {
        String name = part;
        part = env.get(name.substring(1), null);
        if (part == null) throw new IllegalArgumentException(String.format("Invalid url parameter %s", name));
      }
      ret = (ret == null) ? part : (ret+"/"+part);
    }

    return ret;
  }
}
