/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tez.dag.app.web;

import static org.apache.hadoop.yarn.util.StringHelper.pajoin;

import java.net.InetSocketAddress;

import com.google.common.base.Preconditions;
import com.google.inject.name.Names;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.HttpConfig;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.yarn.webapp.WebApp;
import org.apache.hadoop.yarn.webapp.WebApps;
import org.apache.hadoop.yarn.webapp.YarnWebParams;
import org.apache.tez.dag.api.TezUncheckedException;
import org.apache.tez.dag.app.AppContext;

public class WebUIService extends AbstractService {
  private static final String WS_PREFIX = "/ui/ws/v1/tez/";
  public static final String VERTEX_ID = "vertexID";
  public static final String DAG_ID = "dagID";

  private static final Log LOG = LogFactory.getLog(WebUIService.class);

  private final AppContext context;
  private TezAMWebApp tezAMWebApp;
  private WebApp webApp;
  private int port;
  private String historyUrl = "";

  public WebUIService(AppContext context) {
    super(WebUIService.class.getName());
    this.context = context;
    this.tezAMWebApp = new TezAMWebApp(context);
  }

  @Override
  protected void serviceInit(Configuration conf) throws Exception {
    if (historyUrl == null || historyUrl.isEmpty()) {
      LOG.error("Tez UI History URL is not set");
    } else {
      LOG.info("Tez UI History URL: " + historyUrl);
    }

    if (tezAMWebApp != null) {
      this.tezAMWebApp.setHistoryUrl(historyUrl);
    }
    super.serviceInit(conf);
  }

  @Override
  protected void serviceStart() throws Exception {
    if (tezAMWebApp != null) {
      // use AmIpFilter to restrict connections only from the rm proxy
      final Configuration conf = getConfig();
      conf.set("hadoop.http.filter.initializers",
          "org.apache.hadoop.yarn.server.webproxy.amfilter.AmFilterInitializer");
      try {
        // Explicitly disabling SSL for the web service. For https we do not want AM users to allow
        // access to the keystore file for opening SSL listener. We can trust RM/NM to issue SSL
        // certificates, however AM user is not trusted.
        this.webApp = WebApps
            .$for(this.tezAMWebApp)
            .with(conf)
            .withHttpPolicy(conf, HttpConfig.Policy.HTTP_ONLY)
            .start(this.tezAMWebApp);
        this.port = this.webApp.httpServer().getConnectorAddress(0).getPort();
      } catch (Exception e) {
        LOG.error("Tez UI WebService failed to start.", e);
        throw new TezUncheckedException(e);
      }
    }
    super.serviceStart();
  }

  @Override
  protected void serviceStop() throws Exception {
    if (this.webApp != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Stopping WebApp");
      }
      this.webApp.stop();
    }
    super.serviceStop();
  }

  public int getPort() {
    return this.port;
  }

  public String getURL() {
    String url = "";
    InetSocketAddress address = webApp.getListenerAddress();

    if (address != null) {
      final String hostName = address.getAddress().getCanonicalHostName();
      final int port = address.getPort();
      url = "http://" + hostName + ":" + port + "/ui/";
    }

    return url;
  }

  public String getHistoryUrl() {
    return historyUrl;
  }

  public void setHistoryUrl(String historyUrl) {
    this.historyUrl = historyUrl;
  }

  private static class TezAMWebApp extends WebApp implements YarnWebParams {

    private String historyUrl;
    AppContext context;

    public TezAMWebApp(AppContext context) {
      this.context = context;
    }

    public void setHistoryUrl(String historyUrl) {
      this.historyUrl = historyUrl;
    }

    @Override
    public void setup() {
      Preconditions.checkArgument(historyUrl != null);
      bind(AppContext.class).toInstance(context);
      bind(String.class).annotatedWith(Names.named("TezUIHistoryURL")).toInstance(historyUrl);
      route("/", AMWebController.class, "ui");
      route("/ui", AMWebController.class, "ui");
      route("/main", AMWebController.class, "main");
      route(WS_PREFIX + "about", AMWebController.class, "about");
      route(WS_PREFIX + pajoin("dagProgress", DAG_ID), AMWebController.class, "getDagProgress");
      route(WS_PREFIX + pajoin("vertexProgress", VERTEX_ID), AMWebController.class,
          "getVertexProgress");
      route(WS_PREFIX + pajoin("vertexProgresses", VERTEX_ID, DAG_ID), AMWebController.class,
          "getVertexProgresses");
    }
  }
}